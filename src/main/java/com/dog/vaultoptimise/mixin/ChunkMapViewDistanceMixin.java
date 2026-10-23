package com.dog.vaultoptimise.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ars Nouveau's camera mixin cancels setViewDistance after the first loaded chunk, so a view distance change while
 * the server runs (dynview) never sends the other chunks that came into range, and players see half the world
 * missing until they rejoin. This runs vanilla's update over every loaded chunk before that mixin is reached.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapViewDistanceMixin {
    @Shadow int viewDistance;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow public abstract DistanceManager getDistanceManager();

    @Shadow public abstract List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean boundaryOnly);

    @Shadow protected abstract void updateChunkTracking(ServerPlayer player, ChunkPos chunkPos,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, boolean wasLoaded, boolean load);

    @Inject(method = "setViewDistance", at = @At("HEAD"), cancellable = true)
    private void vaultOptimise$updateEveryLoadedChunk(int requestedDistance, CallbackInfo ci) {
        ci.cancel();
        int newDistance = Mth.clamp(requestedDistance + 1, 3, 33);
        if (newDistance == this.viewDistance) {
            return;
        }

        int oldDistance = this.viewDistance;
        this.viewDistance = newDistance;
        ((DistanceManagerInvoker) this.getDistanceManager()).vaultOptimise$updatePlayerTickets(newDistance + 1);

        for (ChunkHolder holder : this.updatingChunkMap.values()) {
            ChunkPos chunkPos = holder.getPos();
            MutableObject<ClientboundLevelChunkWithLightPacket> packetCache = new MutableObject<>();
            for (ServerPlayer player : this.getPlayers(chunkPos, false)) {
                SectionPos playerSection = player.getLastSectionPos();
                boolean wasInRange = ChunkMap.isChunkInRange(chunkPos.x, chunkPos.z, playerSection.x(), playerSection.z(), oldDistance);
                boolean isInRange = ChunkMap.isChunkInRange(chunkPos.x, chunkPos.z, playerSection.x(), playerSection.z(), newDistance);
                this.updateChunkTracking(player, chunkPos, packetCache, wasInRange, isInRange);
            }
        }
    }
}
