package com.dog.vaultoptimise.commands;

import com.dog.vaultoptimise.backup.ChunkBackupInfo;
import com.dog.vaultoptimise.backup.ChunkBackupManager;
import com.dog.vaultoptimise.backup.ChunkLocation;
import com.dog.vaultoptimise.backup.ChunkRecoveryTracker;
import com.dog.vaultoptimise.debug.CmeDebugTrigger;
import com.dog.vaultoptimise.integration.OpacIntegration;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-only command tree for backup management and debug harness control.
 * All subcommands require permission level 2 (op).
 *
 * Tree:
 *   /vaultoptimise backup status                       — print stats and OPaC status
 *   /vaultoptimise backup info &lt;chunkX&gt; &lt;chunkZ&gt;        — show claim + backup details for one chunk
 *   /vaultoptimise backup restore &lt;chunkX&gt; &lt;chunkZ&gt;     — restore a chunk from its backup
 *   /vaultoptimise backup discard &lt;chunkX&gt; &lt;chunkZ&gt;     — delete a chunk's backup file
 *   /vaultoptimise backup broken-list                  — list every persistently broken chunk
 *   /vaultoptimise backup mark-fixed &lt;chunkX&gt; &lt;chunkZ&gt;  — clear the broken flag after fixing the underlying issue
 *   /vaultoptimise debug arm-cme &lt;chunkX&gt; &lt;chunkZ&gt;       — arm synthetic CME for one chunk
 *   /vaultoptimise debug disarm-cme                    — clear armed CME target
 */
@Mod.EventBusSubscriber
public final class BackupCommands {

    private static final DateTimeFormatter ABSOLUTE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BackupCommands() {}

    /**
     * Forge calls this on RegisterCommandsEvent. Adds the backup and debug
     * subtrees under the existing /vaultoptimise root.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("vaultoptimise")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("backup")
                        .then(Commands.literal("status")
                                .executes(BackupCommands::executeBackupStatus))
                        .then(Commands.literal("info")
                                .executes(BackupCommands::executeBackupInfoCurrentChunk)
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(BackupCommands::executeBackupInfo))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(BackupCommands::executeBackupRestore))))
                        .then(Commands.literal("discard")
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(BackupCommands::executeBackupDiscard))))
                        .then(Commands.literal("broken-list")
                                .executes(BackupCommands::executeBackupBrokenList))
                        .then(Commands.literal("mark-fixed")
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(BackupCommands::executeBackupMarkFixed)))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("arm-cme")
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(BackupCommands::executeDebugArmCme))))
                        .then(Commands.literal("disarm-cme")
                                .executes(BackupCommands::executeDebugDisarmCme)))
        );
    }

    /**
     * Prints session counters (backups written, restores succeeded/failed) and
     * OPaC integration status. Useful for confirming the system is wired up and
     * for spotting an actively misbehaving mod via spiking restore counts.
     */
    private static int executeBackupStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        sendColored(source, "VaultOptimise backup status", ChatFormatting.GOLD);
        sendColored(source, "  OPaC integration: " + (OpacIntegration.isLoaded() ? "loaded" : "not loaded"),
                OpacIntegration.isLoaded() ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendColored(source, "  Backups written this session: " + ChunkBackupManager.getBackupsWritten(),
                ChatFormatting.WHITE);
        sendColored(source, "  Restores on save: " + ChunkBackupManager.getRestoresOnSave(),
                ChatFormatting.GREEN);
        sendColored(source, "  Restores on load: " + ChunkBackupManager.getRestoresOnLoad(),
                ChatFormatting.GREEN);
        sendColored(source, "  Failed (no backup available): " + ChunkBackupManager.getRestoresFailedNoBackup(),
                ChatFormatting.YELLOW);
        sendColored(source, "  Failed (retry also failed): " + ChunkBackupManager.getRestoresFailedRetry(),
                ChatFormatting.RED);
        int brokenCount = ChunkRecoveryTracker.brokenCount();
        sendColored(source, "  Persistently broken chunks: " + brokenCount,
                brokenCount == 0 ? ChatFormatting.WHITE : ChatFormatting.RED);
        ChunkPos armedTarget = CmeDebugTrigger.getArmed();
        if (armedTarget != null) {
            sendColored(source, "  Debug CME armed for chunk " + armedTarget.x + "," + armedTarget.z,
                    ChatFormatting.LIGHT_PURPLE);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Lists every chunk currently flagged as persistently broken across all
     * dimensions, one per line. Useful for an operator who wants to walk through
     * each broken location and investigate the underlying mod bug.
     */
    private static int executeBackupBrokenList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var brokenChunks = ChunkRecoveryTracker.snapshotBrokenChunks();
        if (brokenChunks.isEmpty()) {
            sendColored(source, "No chunks are currently flagged as broken.", ChatFormatting.GREEN);
            return Command.SINGLE_SUCCESS;
        }
        sendColored(source, "Persistently broken chunks (" + brokenChunks.size() + "):", ChatFormatting.RED);
        for (ChunkLocation location : brokenChunks) {
            sendColored(source,
                    "  " + location.dimension() + " [" + location.pos().x + ", " + location.pos().z + "]",
                    ChatFormatting.RED);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Clears the broken flag for a chunk in the executing source's current
     * dimension. Use after the operator has investigated and fixed (or removed)
     * the misbehaving block entity. The chunk's recovery counter is reset so the
     * next CME starts a fresh sliding window.
     */
    private static int executeBackupMarkFixed(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        ResourceLocation dimensionId = resolveDimensionId(source);

        boolean cleared = ChunkRecoveryTracker.clearBroken(dimensionId, pos);
        if (cleared) {
            sendColored(source,
                    "Cleared broken flag for chunk " + chunkX + "," + chunkZ + " in " + dimensionId
                            + ". Recovery limit will reset on next CME.",
                    ChatFormatting.GREEN);
        } else {
            sendColored(source,
                    "Chunk " + chunkX + "," + chunkZ + " in " + dimensionId + " was not flagged as broken.",
                    ChatFormatting.GRAY);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * No-argument variant of /backup info: derives the chunk pos from the
     * executing source's current world position so a player can simply run the
     * command from wherever they are standing without typing coordinates.
     */
    private static int executeBackupInfoCurrentChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChunkPos pos = new ChunkPos(new BlockPos(source.getPosition()));
        return runBackupInfo(source, pos);
    }

    /**
     * Argumented variant of /backup info: reads chunkX/chunkZ from the command
     * args. Used by admins inspecting a chunk they are not standing in.
     */
    private static int executeBackupInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        return runBackupInfo(source, new ChunkPos(chunkX, chunkZ));
    }

    /**
     * Prints claim ownership and backup details for a single chunk in the
     * executing source's current dimension. Combines OPaC claim lookup with
     * backup metadata so an admin can answer "is this chunk protected, when was
     * its backup written, and how big is it?" in one shot.
     */
    private static int runBackupInfo(CommandSourceStack source, ChunkPos pos) {
        ResourceLocation dimensionId = resolveDimensionId(source);
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();

        sendColored(source, "Chunk " + pos.x + "," + pos.z + " in " + dimensionId, ChatFormatting.GOLD);

        if (ChunkRecoveryTracker.isBroken(dimensionId, pos)) {
            sendColored(source, "  Status: BROKEN (recovery limit reached — clear with /vaultoptimise backup mark-fixed)",
                    ChatFormatting.RED);
        }

        boolean claimed = OpacIntegration.isClaimed(level, pos);
        if (claimed) {
            String ownerLabel = OpacIntegration.getClaimOwnerId(level, pos)
                    .map(uuid -> formatPlayerLabel(server, uuid))
                    .orElse("unknown");
            sendColored(source, "  Claimed: yes (owner: " + ownerLabel + ")", ChatFormatting.GREEN);
        } else if (OpacIntegration.isLoaded()) {
            sendColored(source, "  Claimed: no", ChatFormatting.GRAY);
        } else {
            sendColored(source, "  Claimed: unknown (OPaC not loaded)", ChatFormatting.GRAY);
        }

        Optional<ChunkBackupInfo> info = ChunkBackupManager.getBackupInfo(server, dimensionId, pos);
        if (info.isEmpty()) {
            sendColored(source, "  Backup: none", ChatFormatting.GRAY);
            if (claimed) {
                sendColored(source,
                        "    (claim is active but the chunk has not yet had a successful save with backups enabled)",
                        ChatFormatting.GRAY);
            }
            return Command.SINGLE_SUCCESS;
        }

        ChunkBackupInfo backup = info.get();
        sendColored(source, "  Backup: present", ChatFormatting.GREEN);
        sendColored(source, "    Path: " + backup.path(), ChatFormatting.WHITE);
        sendColored(source, "    Size: " + formatBytes(backup.fileSize()), ChatFormatting.WHITE);
        sendColored(source, "    Schema version: " + backup.schemaVersion(), ChatFormatting.WHITE);
        sendColored(source,
                "    Created: " + formatAbsoluteTime(backup.createdEpochMillis())
                        + " (" + formatRelativeAge(backup.createdEpochMillis()) + ")",
                ChatFormatting.WHITE);

        boolean dimensionMatches = dimensionId.toString().equals(backup.storedDimension());
        boolean coordsMatch = backup.storedChunkX() == pos.x && backup.storedChunkZ() == pos.z;
        if (!dimensionMatches || !coordsMatch) {
            sendColored(source,
                    "    WARNING: stored metadata mismatch — dimension=" + backup.storedDimension()
                            + " coords=[" + backup.storedChunkX() + "," + backup.storedChunkZ() + "]",
                    ChatFormatting.RED);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Manually restores a chunk in the executing player's current dimension
     * from its backup file. Used as a fallback when automatic recovery failed
     * and the chunk is corrupted, or to roll back a chunk to its last known
     * good state for forensic purposes.
     */
    private static int executeBackupRestore(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        ResourceLocation dimensionId = resolveDimensionId(source);
        MinecraftServer server = source.getServer();

        boolean ok = ChunkBackupManager.restoreManually(server, dimensionId, pos);
        if (ok) {
            sendColored(source, "Restored chunk " + chunkX + "," + chunkZ + " from backup in " + dimensionId,
                    ChatFormatting.GREEN);
        } else {
            sendColored(source,
                    "Failed to restore chunk " + chunkX + "," + chunkZ + " in " + dimensionId
                            + " — no backup file or write failed (see log)",
                    ChatFormatting.RED);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Deletes the backup file for a chunk in the executing player's current
     * dimension. Useful after a chunk has been intentionally regenerated and
     * the old backup is no longer wanted.
     */
    private static int executeBackupDiscard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        ResourceLocation dimensionId = resolveDimensionId(source);
        MinecraftServer server = source.getServer();

        boolean ok = ChunkBackupManager.discardBackup(server, dimensionId, pos);
        if (ok) {
            sendColored(source, "Discarded backup for chunk " + chunkX + "," + chunkZ + " in " + dimensionId,
                    ChatFormatting.YELLOW);
        } else {
            sendColored(source,
                    "No backup to discard for chunk " + chunkX + "," + chunkZ + " in " + dimensionId,
                    ChatFormatting.GRAY);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Arms a synthetic CME for the given chunk. The next time the chunk is
     * saved, the IOWorker mixin emits a partial write and throws CME, exactly
     * mirroring the production failure mode. Warns the player if no backup
     * file exists yet for the chunk, since recovery would be unable to fire.
     */
    private static int executeDebugArmCme(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        CmeDebugTrigger.arm(pos);

        ResourceLocation dimensionId = resolveDimensionId(source);
        boolean backupExists = ChunkBackupManager.hasBackup(source.getServer(), dimensionId, pos);

        sendColored(source, "Armed synthetic CME for chunk " + chunkX + "," + chunkZ
                + " in " + dimensionId + " (one-shot)", ChatFormatting.LIGHT_PURPLE);
        if (backupExists) {
            sendColored(source, "  Backup exists for this chunk — recovery should succeed on save.",
                    ChatFormatting.GRAY);
        } else {
            sendColored(source,
                    "  WARNING: no backup exists yet for this chunk. The chunk must save successfully at"
                            + " least once before arming CME, otherwise recovery has nothing to restore.",
                    ChatFormatting.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Clears any armed CME target without firing.
     */
    private static int executeDebugDisarmCme(CommandContext<CommandSourceStack> context) {
        CmeDebugTrigger.disarm();
        sendColored(context.getSource(), "Synthetic CME disarmed.", ChatFormatting.GRAY);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Returns the dimension ResourceLocation for the command source's current
     * level. For player-issued commands this is the player's dimension; for
     * console commands it's whatever level the source is bound to (overworld
     * by default).
     */
    private static ResourceLocation resolveDimensionId(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        return level.dimension().location();
    }

    private static void sendColored(CommandSourceStack source, String message, ChatFormatting color) {
        source.sendSuccess(new TextComponent(message).withStyle(color), false);
    }

    /**
     * Formats a player UUID as "name (uuid)" if the server's profile cache or
     * online player list can resolve a username, falling back to the bare UUID
     * string if the player is unknown to this server.
     */
    private static String formatPlayerLabel(MinecraftServer server, UUID uuid) {
        if (server != null) {
            var onlinePlayer = server.getPlayerList().getPlayer(uuid);
            if (onlinePlayer != null) {
                return onlinePlayer.getGameProfile().getName() + " (" + uuid + ")";
            }
            Optional<GameProfile> cachedProfile = server.getProfileCache().get(uuid);
            if (cachedProfile.isPresent() && cachedProfile.get().getName() != null) {
                return cachedProfile.get().getName() + " (" + uuid + ")";
            }
        }
        return uuid.toString();
    }

    /**
     * Formats a byte count as a human-readable size with one decimal place.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Formats an epoch-millis timestamp as a server-local "yyyy-MM-dd HH:mm:ss" string.
     */
    private static String formatAbsoluteTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(ABSOLUTE_TIME_FORMAT);
    }

    /**
     * Formats how long ago a timestamp was as a short relative string
     * (e.g., "5m ago", "2h ago", "3d ago"). Future timestamps return "just now"
     * to avoid printing negative durations from clock skew.
     */
    private static String formatRelativeAge(long epochMillis) {
        long deltaSeconds = (System.currentTimeMillis() - epochMillis) / 1000L;
        if (deltaSeconds < 1) return "just now";
        if (deltaSeconds < 60) return deltaSeconds + "s ago";
        long minutes = deltaSeconds / 60L;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60L;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24L;
        return days + "d ago";
    }
}
