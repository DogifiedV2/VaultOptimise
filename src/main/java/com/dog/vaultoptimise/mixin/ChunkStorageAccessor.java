package com.dog.vaultoptimise.mixin;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the IOWorker held by each ChunkStorage. ChunkMap extends ChunkStorage
 * and inherits the worker; we use this accessor at ChunkMap.save time to register
 * the IOWorker→ServerLevel mapping needed for OPaC claim lookups during backups.
 */
@Mixin(ChunkStorage.class)
public interface ChunkStorageAccessor {

    @Accessor("worker")
    IOWorker vaultOptimise$getWorker();
}
