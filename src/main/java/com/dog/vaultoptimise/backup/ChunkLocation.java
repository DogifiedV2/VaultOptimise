package com.dog.vaultoptimise.backup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * Identity of a chunk across the server: dimension plus chunk coordinates.
 * Used as a map key for per-chunk recovery tracking and the broken-chunk set.
 * Records get equals/hashCode for free, which is exactly what we need here.
 */
public record ChunkLocation(ResourceLocation dimension, ChunkPos pos) {}
