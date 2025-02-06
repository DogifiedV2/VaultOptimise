package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.world.IChunkTimeSave;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({ChunkAccess.class})
public class ChunkAccessMixin implements IChunkTimeSave {

    @Unique
    long vaultOptimise$saveTimePoint = 0L;

    public long vaultOptimise$getNextSaveTime() {
        return this.vaultOptimise$saveTimePoint;
    }

    public void vaultOptimise$setSaveTimePoint(long saveTimePoint) {
        this.vaultOptimise$saveTimePoint = saveTimePoint;
    }
}
