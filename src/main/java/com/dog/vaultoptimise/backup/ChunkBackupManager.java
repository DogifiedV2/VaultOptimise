package com.dog.vaultoptimise.backup;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.config.ServerConfig;
import com.dog.vaultoptimise.integration.OpacIntegration;
import com.dog.vaultoptimise.mixin.IOWorkerAccessor;
import com.dog.vaultoptimise.mixin.RegionFileStorageAccessor;
import com.dog.vaultoptimise.mixin.RegionFileStorageInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates per-chunk on-disk backups, save-side recovery (when a chunk write
 * throws ConcurrentModificationException due to a buggy mod racing on NBT), and
 * load-side recovery (when a chunk read fails because a previous write left a
 * truncated region sector behind).
 *
 * Backup writes are gated by Open Parties and Claims: only chunks claimed by a
 * player produce backups. Recovery on load checks for a backup regardless of
 * current claim status, so chunks that were claimed-then-unclaimed still benefit
 * from any backup file still on disk.
 *
 * Backup files live alongside each dimension's region folder under a sibling
 * directory named "vaultoptimise-backups", one .nbt.gz file per chunk:
 *
 *   world/vaultoptimise-backups/c.&lt;chunkX&gt;.&lt;chunkZ&gt;.nbt.gz
 *   world/DIM-1/vaultoptimise-backups/c.&lt;chunkX&gt;.&lt;chunkZ&gt;.nbt.gz
 */
public final class ChunkBackupManager {

    public static final String BACKUP_FOLDER_NAME = "vaultoptimise-backups";

    private static final String REGION_FOLDER_NAME = "region";

    private static final ConcurrentHashMap<IOWorker, ServerLevel> WORKER_TO_LEVEL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<RegionFileStorage, Optional<Path>> BACKUP_DIR_CACHE = new ConcurrentHashMap<>();

    private static final AtomicLong BACKUPS_WRITTEN = new AtomicLong();
    private static final AtomicLong RESTORES_ON_SAVE = new AtomicLong();
    private static final AtomicLong RESTORES_ON_LOAD = new AtomicLong();
    private static final AtomicLong RESTORES_FAILED_NO_BACKUP = new AtomicLong();
    private static final AtomicLong RESTORES_FAILED_RETRY = new AtomicLong();

    private ChunkBackupManager() {}

    /**
     * Records the IOWorker→ServerLevel association so save-time and load-time
     * hooks can resolve which dimension a chunk belongs to. Idempotent: subsequent
     * calls with the same worker do not overwrite the registration.
     */
    public static void ensureRegistered(IOWorker worker, ServerLevel level) {
        if (worker == null || level == null) return;
        WORKER_TO_LEVEL.putIfAbsent(worker, level);
    }

    /**
     * Looks up the previously registered ServerLevel for this IOWorker. Returns
     * null if the worker is unknown — for example a non-chunk IOWorker used by
     * the entity or POI subsystems, which we never want to back up.
     */
    public static ServerLevel resolveLevel(IOWorker worker) {
        return WORKER_TO_LEVEL.get(worker);
    }

    /** Remove strong references after the worker has closed its mailbox and storage. */
    public static void unregister(IOWorker worker) {
        WORKER_TO_LEVEL.remove(worker);
        forgetStorage(((IOWorkerAccessor) (Object) worker).vaultOptimise$getStorage());
    }

    public static void forgetStorage(RegionFileStorage storage) {
        BACKUP_DIR_CACHE.remove(storage);
    }

    /**
     * Trigger point invoked from the save mixin after a successful chunk write.
     * If chunk backups are enabled and the chunk is currently claimed in OPaC,
     * asynchronously writes a backup copy of the chunk's NBT to disk via
     * Util.ioPool. Backup failures are logged but never thrown — backups are
     * best-effort and never delay save completion.
     */
    public static void onSaveSuccess(IOWorker worker, ChunkPos pos, CompoundTag data) {
        if (!ServerConfig.CONFIG_VALUES.enableChunkBackups.get()) return;
        if (data == null) return;

        ServerLevel level = resolveLevel(worker);
        if (level == null) return;
        if (!OpacIntegration.isClaimed(level, pos)) return;

        Path backupFile = backupFileFor(worker, pos);
        if (backupFile == null) return;

        ResourceLocation dimensionId = level.dimension().location();
        Util.ioPool().submit(() -> writeBackupQuietly(backupFile, data, dimensionId, pos));
    }

    /**
     * Save-side recovery hook invoked from the save mixin when the IOWorker's
     * write throws ConcurrentModificationException. Looks up the chunk's backup
     * on disk; if found, retries the write with the backup tag, which overwrites
     * the partial corrupted region sector left behind by the failed write.
     *
     * Returns true on successful recovery, false otherwise. The caller should
     * treat false as a permanent save failure (vanilla will eventually
     * regenerate the chunk on next load if no backup is recoverable).
     */
    public static boolean tryRecoverFromCmeAndRetry(
            IOWorker worker,
            RegionFileStorage storage,
            ChunkPos pos,
            Throwable originalCause) {
        if (!ServerConfig.CONFIG_VALUES.enableSaveRecovery.get()) return false;
        if (ServerConfig.CONFIG_VALUES.debugSuppressRecovery.get()) return false;

        ServerLevel level = resolveLevel(worker);
        String dimensionLabel = level != null ? level.dimension().location().toString() : "unknown-dimension";

        Optional<CompoundTag> backupTag = readBackupFor(worker, pos);
        if (backupTag.isEmpty()) {
            RESTORES_FAILED_NO_BACKUP.incrementAndGet();
            VaultOptimise.LOGGER.warn(
                    "VaultOptimise: chunk save failed (CME) for {} {} but no backup is available — chunk will be regenerated on next load",
                    dimensionLabel, pos, originalCause);
            return false;
        }

        try {
            ((RegionFileStorageInvoker) (Object) storage).vaultOptimise$invokeWrite(pos, backupTag.get());
            RESTORES_ON_SAVE.incrementAndGet();
            VaultOptimise.LOGGER.warn(
                    "VaultOptimise: recovered chunk {} {} from backup after a save-time CME (a mod was racing on chunk NBT during write)",
                    dimensionLabel, pos);
            handlePostRecoveryDecision(level, pos);
            return true;
        } catch (Exception retryFailure) {
            RESTORES_FAILED_RETRY.incrementAndGet();
            VaultOptimise.LOGGER.error(
                    "VaultOptimise: backup-restore retry also failed for {} {} — region sector remains corrupted, admin intervention needed",
                    dimensionLabel, pos, retryFailure);
            return false;
        }
    }

    /**
     * After a successful backup-restore, decides whether to mark the chunk dirty
     * (so the next save cycle re-saves the live in-memory state) or to flag the
     * chunk as persistently broken and stop fighting it. Persistent-broken
     * triggers a loud ERROR log and an op-broadcast so a human knows to look.
     */
    private static void handlePostRecoveryDecision(ServerLevel level, ChunkPos pos) {
        if (level == null) return;
        ResourceLocation dimensionId = level.dimension().location();
        ChunkRecoveryTracker.RecoveryDecision decision = ChunkRecoveryTracker.recordRecovery(dimensionId, pos);
        switch (decision) {
            case REDIRTY -> markChunkDirtyOnMainThread(level, pos);
            case NEWLY_BROKEN -> announcePersistentlyBrokenChunk(level, pos);
            case STILL_BROKEN -> VaultOptimise.LOGGER.warn(
                    "VaultOptimise: chunk {} {} CME'd again but is already flagged broken; not redirtying",
                    dimensionId, pos);
        }
    }

    /**
     * Schedules a setUnsaved(true) on the main thread for the loaded chunk at
     * the given position. The ChunkSource's getChunkNow only returns non-null
     * on the main thread, so the work has to bounce there. If the chunk is no
     * longer loaded by the time the task runs, we silently no-op — the in-memory
     * state already wrote out via the unload path or never existed.
     */
    private static void markChunkDirtyOnMainThread(ServerLevel level, ChunkPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                chunk.setUnsaved(true);
            }
        });
    }

    /**
     * Logs at ERROR and broadcasts a chat message to all online operators when
     * a chunk hits the recovery limit. The chunk is now flagged broken in the
     * tracker, players entering it will see the recurring warning, and the
     * operator can investigate via /vaultoptimise backup info.
     */
    private static void announcePersistentlyBrokenChunk(ServerLevel level, ChunkPos pos) {
        ResourceLocation dimensionId = level.dimension().location();
        VaultOptimise.LOGGER.error(
                "VaultOptimise: chunk {} {} has hit the recovery limit ({} CMEs in {} minutes) — flagging as broken. Players entering it will see a warning. A mod is persistently racing on this chunk's NBT; investigate the chunk's block entities.",
                dimensionId, pos, ChunkRecoveryTracker.RECOVERY_LIMIT, ChunkRecoveryTracker.WINDOW_MILLIS / 60_000L);

        MinecraftServer server = level.getServer();
        if (server == null) return;
        TextComponent alert = new TextComponent(
                "[VaultOptimise] Chunk " + pos.x + "," + pos.z + " in " + dimensionId
                        + " has been flagged as persistently broken. Players inside will receive warnings."
        );
        alert.withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendMessage(alert, player.getUUID());
            }
        }
    }

    /**
     * Load-side recovery hook invoked from RegionFileStorageMixin when an NbtIo
     * read throws (typically EOFException from a previously corrupted region
     * sector). Returns the backup chunk tag if a usable backup is on disk,
     * otherwise empty — which lets vanilla's existing failure path regenerate
     * the chunk.
     *
     * Backup lookup is unconditional on current claim status, so a chunk that
     * was claimed-then-unclaimed still gets recovered from its lingering backup.
     */
    public static Optional<CompoundTag> tryLoadBackupByStorage(RegionFileStorage storage, ChunkPos pos) {
        if (!ServerConfig.CONFIG_VALUES.enableLoadRecovery.get()) return Optional.empty();

        Path file = backupFileFor(storage, pos);
        if (file == null) return Optional.empty();

        Optional<CompoundTag> backup = readBackupAt(file);
        if (backup.isPresent()) {
            RESTORES_ON_LOAD.incrementAndGet();
            VaultOptimise.LOGGER.warn(
                    "VaultOptimise: restoring chunk {} from backup file {} due to read failure (region sector was corrupted)",
                    pos, file.getFileName());
        }
        return backup;
    }

    /**
     * Manual restore entry point used by the admin command. Writes the backup
     * tag directly to the dimension's region file via the same IOWorker storage
     * the live save path uses. Returns false if the dimension is not loaded, the
     * IOWorker is not registered, or no backup exists for the chunk.
     */
    public static boolean restoreManually(MinecraftServer server, ResourceLocation dimensionId, ChunkPos pos) {
        ServerLevel level = lookupLevel(server, dimensionId);
        if (level == null) return false;

        IOWorker worker = findWorkerForLevel(level);
        if (worker == null) return false;

        Optional<CompoundTag> backup = readBackupFor(worker, pos);
        if (backup.isEmpty()) return false;

        RegionFileStorage storage = ((IOWorkerAccessor) (Object) worker).vaultOptimise$getStorage();
        try {
            ((RegionFileStorageInvoker) (Object) storage).vaultOptimise$invokeWrite(pos, backup.get());
            VaultOptimise.LOGGER.info("VaultOptimise: manual restore wrote chunk {} {}", dimensionId, pos);
            return true;
        } catch (Exception failure) {
            VaultOptimise.LOGGER.error("VaultOptimise: manual restore failed for {} {}", dimensionId, pos, failure);
            return false;
        }
    }

    /**
     * Manual discard entry point used by the admin command. Deletes the backup
     * file for the given chunk if one exists. Returns false if the dimension is
     * not loaded, the IOWorker is not registered, or the file could not be deleted.
     */
    public static boolean discardBackup(MinecraftServer server, ResourceLocation dimensionId, ChunkPos pos) {
        ServerLevel level = lookupLevel(server, dimensionId);
        if (level == null) return false;

        IOWorker worker = findWorkerForLevel(level);
        if (worker == null) return false;

        Path file = backupFileFor(worker, pos);
        if (file == null) return false;

        try {
            return Files.deleteIfExists(file);
        } catch (IOException failure) {
            VaultOptimise.LOGGER.error("VaultOptimise: failed to discard backup for {} {}", dimensionId, pos, failure);
            return false;
        }
    }

    /**
     * Returns true if a backup file currently exists for the given chunk.
     * Used by status reporting to indicate whether a chunk is recoverable.
     */
    public static boolean hasBackup(MinecraftServer server, ResourceLocation dimensionId, ChunkPos pos) {
        ServerLevel level = lookupLevel(server, dimensionId);
        if (level == null) return false;
        IOWorker worker = findWorkerForLevel(level);
        if (worker == null) return false;
        Path file = backupFileFor(worker, pos);
        return file != null && Files.exists(file);
    }

    /**
     * Returns metadata about the backup file for the given chunk, if one exists.
     * Reads only the wrapper header — schema version, creation timestamp, and the
     * stamped dimension/chunk coordinates — so the admin command can display
     * backup details without paying the cost of fully parsing the chunk Data tag.
     */
    public static Optional<ChunkBackupInfo> getBackupInfo(MinecraftServer server, ResourceLocation dimensionId, ChunkPos pos) {
        ServerLevel level = lookupLevel(server, dimensionId);
        if (level == null) return Optional.empty();
        IOWorker worker = findWorkerForLevel(level);
        if (worker == null) return Optional.empty();
        Path file = backupFileFor(worker, pos);
        if (file == null || !Files.exists(file)) return Optional.empty();
        return readBackupMetadata(file);
    }

    public static long getBackupsWritten() { return BACKUPS_WRITTEN.get(); }
    public static long getRestoresOnSave() { return RESTORES_ON_SAVE.get(); }
    public static long getRestoresOnLoad() { return RESTORES_ON_LOAD.get(); }
    public static long getRestoresFailedNoBackup() { return RESTORES_FAILED_NO_BACKUP.get(); }
    public static long getRestoresFailedRetry() { return RESTORES_FAILED_RETRY.get(); }

    /**
     * Finds the registered IOWorker for the given level by scanning the registry.
     * The registry is small (one entry per dimension) so a linear scan is cheap.
     */
    private static IOWorker findWorkerForLevel(ServerLevel target) {
        for (var entry : WORKER_TO_LEVEL.entrySet()) {
            if (entry.getValue() == target) return entry.getKey();
        }
        return null;
    }

    /**
     * Resolves a ServerLevel from a dimension ResourceLocation via the standard
     * registry key construction. Returns null if no such dimension is loaded.
     */
    private static ServerLevel lookupLevel(MinecraftServer server, ResourceLocation dimensionId) {
        if (server == null || dimensionId == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
        return server.getLevel(key);
    }

    /**
     * Computes the on-disk backup file Path for a chunk via its IOWorker. Delegates
     * to the storage-keyed variant after extracting the underlying RegionFileStorage.
     */
    private static Path backupFileFor(IOWorker worker, ChunkPos pos) {
        if (worker == null) return null;
        RegionFileStorage storage = ((IOWorkerAccessor) (Object) worker).vaultOptimise$getStorage();
        return backupFileFor(storage, pos);
    }

    /**
     * Computes the on-disk backup file Path for a chunk via its RegionFileStorage.
     * Returns null when the storage isn't a chunk storage (the resolved backup
     * directory is cached per-storage so the folder-name check and Path resolution
     * only run once per dimension instead of once per save).
     */
    private static Path backupFileFor(RegionFileStorage storage, ChunkPos pos) {
        if (storage == null) return null;
        Path backupDirectory = BACKUP_DIR_CACHE.computeIfAbsent(storage, ChunkBackupManager::resolveBackupDirectory).orElse(null);
        if (backupDirectory == null) return null;
        return backupDirectory.resolve("c." + pos.x + "." + pos.z + ".nbt.gz");
    }

    /**
     * Resolves the backup directory for a storage and creates it on disk.
     * Returns empty for non-chunk storages (POI, entities) so their reads and
     * writes never accidentally collide with chunk backup paths. Called once
     * per RegionFileStorage via computeIfAbsent.
     */
    private static Optional<Path> resolveBackupDirectory(RegionFileStorage storage) {
        Path regionFolder = ((RegionFileStorageAccessor) (Object) storage).vaultOptimise$getFolder();
        if (regionFolder == null) return Optional.empty();
        Path folderName = regionFolder.getFileName();
        if (folderName == null || !REGION_FOLDER_NAME.equals(folderName.toString())) return Optional.empty();
        Path dimensionFolder = regionFolder.getParent();
        if (dimensionFolder == null) return Optional.empty();

        Path backupDirectory = dimensionFolder.resolve(BACKUP_FOLDER_NAME);
        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException createFailure) {
            VaultOptimise.LOGGER.warn(
                    "VaultOptimise: could not pre-create backup directory {}: {}",
                    backupDirectory, createFailure.toString());
        }
        return Optional.of(backupDirectory);
    }

    /**
     * Reads, validates, and unwraps a backup file for the given chunk via its
     * IOWorker. Returns the inner Data CompoundTag if the file exists, parses
     * cleanly, and matches the current schema version; otherwise returns empty.
     */
    private static Optional<CompoundTag> readBackupFor(IOWorker worker, ChunkPos pos) {
        Path file = backupFileFor(worker, pos);
        if (file == null) return Optional.empty();
        return readBackupAt(file);
    }

    /**
     * Reads only the metadata fields from a backup wrapper. The full file is
     * decompressed (NbtIo doesn't expose a partial reader) but the heavy Data
     * subtag is never touched after parsing, so we avoid building admin output
     * around the entire chunk NBT in memory.
     */
    private static Optional<ChunkBackupInfo> readBackupMetadata(Path file) {
        try {
            CompoundTag wrapper = NbtIo.readCompressed(file.toFile());
            if (wrapper == null) return Optional.empty();
            long fileSize = Files.size(file);
            return Optional.of(new ChunkBackupInfo(
                    file,
                    fileSize,
                    wrapper.getInt(ChunkBackupSchema.KEY_VERSION),
                    wrapper.getLong(ChunkBackupSchema.KEY_CREATED),
                    wrapper.getString(ChunkBackupSchema.KEY_DIMENSION),
                    wrapper.getInt(ChunkBackupSchema.KEY_CHUNK_X),
                    wrapper.getInt(ChunkBackupSchema.KEY_CHUNK_Z)
            ));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException readFailure) {
            VaultOptimise.LOGGER.warn(
                    "VaultOptimise: failed to read backup metadata from {}: {}",
                    file, readFailure.toString());
            return Optional.empty();
        }
    }

    /**
     * Reads, validates, and unwraps a backup file at a specific path. NoSuchFileException
     * is treated as "no backup available" (returns empty silently); other IOExceptions
     * are logged because they indicate real corruption of the backup itself.
     */
    private static Optional<CompoundTag> readBackupAt(Path file) {
        try {
            CompoundTag wrapper = NbtIo.readCompressed(file.toFile());
            if (wrapper == null) return Optional.empty();
            int version = wrapper.getInt(ChunkBackupSchema.KEY_VERSION);
            if (version != ChunkBackupSchema.CURRENT_VERSION) {
                VaultOptimise.LOGGER.warn(
                        "VaultOptimise: backup file {} has schema version {} (expected {}); ignoring",
                        file, version, ChunkBackupSchema.CURRENT_VERSION);
                return Optional.empty();
            }
            CompoundTag data = wrapper.getCompound(ChunkBackupSchema.KEY_DATA);
            if (data.isEmpty()) return Optional.empty();
            return Optional.of(data);
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException readFailure) {
            VaultOptimise.LOGGER.warn("VaultOptimise: failed to read backup file {}: {}", file, readFailure.toString());
            return Optional.empty();
        }
    }

    /**
     * Writes the backup envelope using a temp-file-and-atomic-rename pattern so
     * the on-disk backup is never half-written. Falls back to a non-atomic move
     * on filesystems that don't support atomic rename. The parent directory is
     * created once per storage in resolveBackupDirectory, so this method skips
     * the createDirectories syscall on every save. Logs and counts failures but
     * never throws — backup writes are best-effort.
     */
    private static void writeBackupQuietly(Path target, CompoundTag data, ResourceLocation dimensionId, ChunkPos pos) {
        try {
            Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");

            CompoundTag wrapper = ChunkBackupSchema.wrap(data, dimensionId, pos);
            NbtIo.writeCompressed(wrapper, tempFile.toFile());

            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicNotSupported) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }

            BACKUPS_WRITTEN.incrementAndGet();
            if (ServerConfig.CONFIG_VALUES.debugLogging.get()) {
                VaultOptimise.LOGGER.info("VaultOptimise: wrote chunk backup for {} {}", dimensionId, pos);
            }
        } catch (Exception backupFailure) {
            VaultOptimise.LOGGER.warn(
                    "VaultOptimise: failed to write chunk backup for {} {}: {}",
                    dimensionId, pos, backupFailure.toString());
        }
    }
}
