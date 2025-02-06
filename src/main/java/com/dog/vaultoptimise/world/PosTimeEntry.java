package com.dog.vaultoptimise.world;

import net.minecraft.world.level.ChunkPos;

public class PosTimeEntry {

    public final long savetime;
    public final ChunkPos pos;

    public PosTimeEntry(long savetime, ChunkPos pos) {
        this.savetime = savetime;
        this.pos = pos;
    }
}
