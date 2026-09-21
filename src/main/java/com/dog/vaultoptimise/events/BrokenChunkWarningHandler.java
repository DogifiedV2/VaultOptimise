package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.backup.ChunkRecoveryTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Tick handler that warns any player standing in a chunk flagged as persistently
 * broken by ChunkRecoveryTracker. Fires every 100 ticks (5 seconds at 20 TPS).
 * No-ops when the broken-chunk set is empty so the per-tick cost is negligible
 * on healthy servers.
 */
@Mod.EventBusSubscriber
public final class BrokenChunkWarningHandler {

    private static final long WARNING_INTERVAL_TICKS = 100L;
    private static final String WARNING_MESSAGE =
            "This chunk has a major issue, please contact any staff member and don't interact with it";

    private static long tickCounter = 0L;

    private BrokenChunkWarningHandler() {}

    /**
     * Forge tick callback. Skips the body unless a full warning interval has
     * elapsed and at least one chunk is flagged broken — both checks are cheap.
     * When the message fires it iterates online players and sends the warning
     * directly to anyone whose current chunk is in the broken set.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % WARNING_INTERVAL_TICKS != 0L) return;
        if (ChunkRecoveryTracker.brokenCount() == 0) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        TextComponent warning = new TextComponent(WARNING_MESSAGE);
        warning.withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkPos chunkPos = player.chunkPosition();
            ResourceLocation dimensionId = player.getLevel().dimension().location();
            if (ChunkRecoveryTracker.isBroken(dimensionId, chunkPos)) {
                player.sendMessage(warning, player.getUUID());
            }
        }
    }
}
