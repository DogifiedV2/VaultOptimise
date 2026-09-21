package com.dog.vaultoptimise.backup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * On-disk format constants for VaultOptimise chunk backups. Each backup file
 * stores a wrapper CompoundTag containing schema version, creation timestamp,
 * dimension and chunk metadata, and the raw chunk NBT under the Data key. The
 * wrapper makes backups self-describing so future schema versions can detect
 * and migrate older files.
 *
 * Current version: 1.
 */
public final class ChunkBackupSchema {

    public static final int CURRENT_VERSION = 1;

    public static final String KEY_VERSION = "Version";
    public static final String KEY_CREATED = "Created";
    public static final String KEY_DIMENSION = "Dimension";
    public static final String KEY_CHUNK_X = "ChunkX";
    public static final String KEY_CHUNK_Z = "ChunkZ";
    public static final String KEY_DATA = "Data";

    private ChunkBackupSchema() {}

    /**
     * Wraps a raw chunk CompoundTag in the backup envelope, attaching schema
     * version, creation timestamp, and dimension/chunk metadata. The returned tag
     * is what gets serialized to the .nbt.gz backup file.
     */
    public static CompoundTag wrap(CompoundTag chunkData, ResourceLocation dimensionId, ChunkPos pos) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.putInt(KEY_VERSION, CURRENT_VERSION);
        wrapper.putLong(KEY_CREATED, System.currentTimeMillis());
        wrapper.putString(KEY_DIMENSION, dimensionId != null ? dimensionId.toString() : "unknown");
        wrapper.putInt(KEY_CHUNK_X, pos.x);
        wrapper.putInt(KEY_CHUNK_Z, pos.z);
        wrapper.put(KEY_DATA, chunkData);
        return wrapper;
    }
}
