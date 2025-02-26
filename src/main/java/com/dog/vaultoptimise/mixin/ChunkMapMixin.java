package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.config.ServerConfig;
import com.dog.vaultoptimise.world.IChunkTimeSave;
import com.dog.vaultoptimise.world.PosTimeEntry;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayDeque;

@Mixin({ChunkMap.class})
public abstract class ChunkMapMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> emptyMap = new Long2ObjectLinkedOpenHashMap<>();
    private final ArrayDeque<PosTimeEntry> scheduledChunkSaves = new ArrayDeque<>();
    private int checked = 0;
    private int processed = 0;
    private final int chunksPerTick = ServerConfig.CONFIG_VALUES.chunksPerTick.get();


    @Redirect(
            method = {"processUnloads"},
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;")
    )
/**
 * Redirects the original call to Long2ObjectLinkedOpenHashMap.values() in the processUnloads method
 * to perform smooth, staggered chunk saving.
 * <p>
 * This method periodically (every 64 game ticks) checks each visible chunk holder from {@code visibleChunkMap}
 * to determine if it has been accessed since its last save. If so, it retrieves the corresponding chunk.
 * For chunks that are unsaved and of a supported type (either an ImposterProtoChunk or a LevelChunk),
 * it manages a scheduled save time.
 * </p>
 * <p>
 * If a chunk's save time is not yet set (i.e. equals zero), the method schedules a new save time based on the
 * server configuration's chunk save delay and a small random offset. The scheduled save is stored in
 * {@code scheduledChunkSaves} as a {@code PosTimeEntry}.
 * Otherwise, if the current game time exceeds the scheduled save time, it resets the save time to zero.
 * </p>
 * <p>
 * Additionally, the method attempts to process up to 10 scheduled chunk saves per tick. For each scheduled save,
 * if the current game time exceeds the scheduled save time, it retrieves the corresponding chunk holder from
 * {@code visibleChunkMap} using the saved position, calls {@code saveChunkIfNeeded} on it, and removes the
 * entry from the schedule.
 * </p>
 * <p>
 * Finally, if any chunks were saved during this tick and debug logging is enabled, a log message is printed.
 * The method returns an empty collection, as it replaces the original call to {@code values()}.
 * </p>
 *
 * @param instance The Long2ObjectLinkedOpenHashMap instance from which values() was originally being called.
 *                 (Ignored in favor of {@code visibleChunkMap} in this implementation.)
 * @return An empty ObjectCollection of ChunkHolder objects.
 */
    public ObjectCollection<ChunkHolder> smoothChunksaveChunks(Long2ObjectLinkedOpenHashMap instance) {
        long currentGameTime = this.level.getGameTime();

        // Every 64 ticks, iterate over all visible chunks and schedule them for saving if needed.
        if (currentGameTime % 64L == 0L) {
            checked = 0;
            processed = 0;
            for (ObjectIterator<ChunkHolder> objectIterator = this.visibleChunkMap.values().iterator(); objectIterator.hasNext(); ) { ChunkHolder entry = objectIterator.next();
                // Skip chunks that haven't been accessible since their last save.
                checked++;
                if (!entry.wasAccessibleSinceLastSave()) {
                    continue;
                }


                // Retrieve the chunk to be saved. If not available, skip
                ChunkAccess chunkAccess = entry.getChunkToSave().getNow(null);
                // Only process valid chunk types (ImposterProtoChunk or LevelChunk).
                if (!(chunkAccess instanceof net.minecraft.world.level.chunk.ImposterProtoChunk)
                        && !(chunkAccess instanceof net.minecraft.world.level.chunk.LevelChunk)) {
                    continue;
                }


                // If the chunk is unsaved, manage its scheduled save time.
                if (chunkAccess.isUnsaved()) {
                    long saveTimePoint = ((IChunkTimeSave) chunkAccess).vaultOptimise$getNextSaveTime();
                    if (saveTimePoint == 0L) {
                        // Set a new save time using the configured delay and a random offset.
                        ((IChunkTimeSave) chunkAccess).vaultOptimise$setSaveTimePoint(
                                currentGameTime + (ServerConfig.CONFIG_VALUES.chunkSaveDelay.get() * 20L)
                                        + (VaultOptimise.rand.nextInt(20) * 20)
                        );
                        // Queue the scheduled save.
                        this.scheduledChunkSaves.addLast(
                                new PosTimeEntry(((IChunkTimeSave) chunkAccess).vaultOptimise$getNextSaveTime(), entry.getPos())
                        );
                        processed++;
                        continue;
                    }
                    // Reset the save time if the current game time has passed the scheduled time.
                    if (currentGameTime > saveTimePoint) {
                        ((IChunkTimeSave) chunkAccess).vaultOptimise$setSaveTimePoint(0L);
                    }
                }
            }
            if (processed > 0 && ServerConfig.CONFIG_VALUES.debugLogging.get()) {
                VaultOptimise.LOGGER.info("Amount of chunks processed: " + processed);
                VaultOptimise.LOGGER.info("Amount of chunks checked: " + checked);
            }
        }

        int chunksSaved = 0;
        // Process up to 10 scheduled chunk saves per tick.
        for (int i = 0; i < chunksPerTick; ) {
            PosTimeEntry posTimeEntry = this.scheduledChunkSaves.peek();
            if (posTimeEntry == null) {
                break;
            }

            // If the scheduled save time has been reached, process the save.
            if (currentGameTime > posTimeEntry.savetime) {
                ChunkHolder holder = this.visibleChunkMap.get(posTimeEntry.pos.toLong());
                if (holder != null) {
                    saveChunkIfNeeded(holder);
                    chunksSaved++;
                }
                this.scheduledChunkSaves.pop();
                i++;
            } else {
                break;
            }
        }

        // Log the number of chunks saved this tick if debug logging is enabled.
        if (chunksSaved > 0 && ServerConfig.CONFIG_VALUES.debugLogging.get().booleanValue()) {
            VaultOptimise.LOGGER.info("VaultOptimise saved " + chunksSaved + " chunks this tick");
        }

        // Return an empty collection to replace the original values() call.
        return this.emptyMap.values();
    }


    @Shadow
    protected abstract boolean save(ChunkAccess paramChunkAccess);

    @Shadow
    protected abstract boolean saveChunkIfNeeded(ChunkHolder paramChunkHolder);

    @Shadow
    public abstract void broadcast(Entity paramEntity, Packet<?> paramPacket);


}
