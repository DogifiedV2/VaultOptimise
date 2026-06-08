package com.dog.vaultoptimise.mixin;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

/**
 * Exposes the region folder Path so the backup manager can place backup files
 * alongside the dimension's region folder (e.g. world/vaultoptimise-backups for
 * overworld, world/DIM-1/vaultoptimise-backups for nether).
 */
@Mixin(RegionFileStorage.class)
public interface RegionFileStorageAccessor {

    @Accessor("folder")
    Path vaultOptimise$getFolder();
}
