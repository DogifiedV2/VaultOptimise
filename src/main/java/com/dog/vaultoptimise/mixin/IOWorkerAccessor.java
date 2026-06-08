package com.dog.vaultoptimise.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes IOWorker's private RegionFileStorage so the backup manager can call
 * storage.write directly during recovery (overwriting a partially corrupted
 * region sector with valid backup data).
 */
@Mixin(IOWorker.class)
public interface IOWorkerAccessor {

    @Accessor("storage")
    RegionFileStorage vaultOptimise$getStorage();
}
