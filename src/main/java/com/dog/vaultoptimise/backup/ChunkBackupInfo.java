package com.dog.vaultoptimise.backup;

import java.nio.file.Path;

/**
 * Read-only metadata about a chunk backup file. Returned by ChunkBackupManager
 * to admin commands so they can display backup details without re-parsing the
 * wrapper themselves.
 *
 * fileSize is the on-disk gzipped size in bytes. createdEpochMillis comes from
 * the Created field of the schema wrapper, which is set when the backup is
 * written. storedDimension/storedChunkX/storedChunkZ are stamped into the
 * wrapper for sanity checks against the requested chunk.
 */
public record ChunkBackupInfo(
        Path path,
        long fileSize,
        int schemaVersion,
        long createdEpochMillis,
        String storedDimension,
        int storedChunkX,
        int storedChunkZ
) {}
