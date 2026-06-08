package com.dog.vaultoptimise.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;

/**
 * Exposes RegionFileStorage's package-private write method so the backup
 * recovery path can rewrite a corrupted region sector with the contents of a
 * disk backup. Without this invoker the call site fails to compile because
 * write is protected and our code lives outside the storage package.
 */
@Mixin(RegionFileStorage.class)
public interface RegionFileStorageInvoker {

    @Invoker("write")
    void vaultOptimise$invokeWrite(ChunkPos pos, CompoundTag data) throws IOException;
}
