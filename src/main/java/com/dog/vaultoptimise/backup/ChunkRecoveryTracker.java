package com.dog.vaultoptimise.backup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-chunk recovery activity and decides when a chunk should be flagged
 * as persistently broken. The first few CMEs on a chunk are treated as transient
 * (a one-tick race that won't repeat); beyond a threshold we stop redirtying and
 * mark the chunk so operators can investigate the underlying mod bug.
 *
 * Defaults: a chunk that recovers more than RECOVERY_LIMIT times within
 * WINDOW_MILLIS gets marked broken. The window slides — if recoveries stop for
 * the window duration, the counter resets and the chunk gets its full quota
 * again on the next race.
 */
public final class ChunkRecoveryTracker {

    public static final int RECOVERY_LIMIT = 3;
    public static final long WINDOW_MILLIS = 10L * 60L * 1000L;

    private static final ConcurrentHashMap<ChunkLocation, RecoveryWindow> RECOVERIES = new ConcurrentHashMap<>();
    private static final Set<ChunkLocation> BROKEN_CHUNKS = ConcurrentHashMap.newKeySet();

    private ChunkRecoveryTracker() {}

    /**
     * Sliding-window counter for one chunk. count is the recoveries seen inside
     * the current window; firstRecoveryMillis is when the window started.
     */
    private static final class RecoveryWindow {
        int count;
        long firstRecoveryMillis;
    }

    /**
     * Outcome of recording a recovery event. REDIRTY means callers should mark
     * the chunk dirty for re-save. NEWLY_BROKEN means this recovery pushed the
     * chunk past the limit and it should now alert + stop redirtying. STILL_BROKEN
     * means the chunk was already over-limit; recovery still wrote backup data
     * but no further redirty or alert should fire.
     */
    public enum RecoveryDecision {
        REDIRTY,
        NEWLY_BROKEN,
        STILL_BROKEN
    }

    /**
     * Records a recovery event for the given chunk and returns what the caller
     * should do next. Idempotent on the broken-set side: once a chunk is flagged
     * broken, subsequent calls return STILL_BROKEN until clearBroken is invoked.
     */
    public static RecoveryDecision recordRecovery(ResourceLocation dimension, ChunkPos pos) {
        ChunkLocation key = new ChunkLocation(dimension, pos);
        if (BROKEN_CHUNKS.contains(key)) {
            return RecoveryDecision.STILL_BROKEN;
        }
        long now = System.currentTimeMillis();
        RecoveryWindow window = RECOVERIES.computeIfAbsent(key, k -> new RecoveryWindow());
        synchronized (window) {
            if (now - window.firstRecoveryMillis > WINDOW_MILLIS) {
                window.firstRecoveryMillis = now;
                window.count = 0;
            }
            window.count++;
            if (window.count > RECOVERY_LIMIT) {
                BROKEN_CHUNKS.add(key);
                RECOVERIES.remove(key);
                return RecoveryDecision.NEWLY_BROKEN;
            }
        }
        return RecoveryDecision.REDIRTY;
    }

    /**
     * Returns true when the chunk has been flagged as persistently broken.
     * Used by the player-warning tick handler to decide who to message.
     */
    public static boolean isBroken(ResourceLocation dimension, ChunkPos pos) {
        return BROKEN_CHUNKS.contains(new ChunkLocation(dimension, pos));
    }

    /**
     * Returns a snapshot of the broken chunk set. Safe to iterate without holding
     * any lock; later mutations to the underlying set are not reflected.
     */
    public static Set<ChunkLocation> snapshotBrokenChunks() {
        return Set.copyOf(BROKEN_CHUNKS);
    }

    /**
     * Returns the current count of broken chunks. Cheap; reads only the set size.
     */
    public static int brokenCount() {
        return BROKEN_CHUNKS.size();
    }

    /**
     * Removes the broken flag for a chunk and resets its sliding-window counter.
     * Called by the admin command after the operator has investigated and fixed
     * the underlying mod bug. The next CME on this chunk starts fresh.
     */
    public static boolean clearBroken(ResourceLocation dimension, ChunkPos pos) {
        ChunkLocation key = new ChunkLocation(dimension, pos);
        RECOVERIES.remove(key);
        return BROKEN_CHUNKS.remove(key);
    }
}
