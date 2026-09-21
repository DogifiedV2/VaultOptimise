package com.dog.vaultoptimise.debug;

import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds debug state for forcing a synthetic ConcurrentModificationException into
 * the chunk save path of a single targeted chunk. One-shot: the trigger disarms
 * itself after firing once, so a single arming command produces exactly one
 * synthetic CME and no further interference.
 *
 * Used by the IOWorker save mixin to verify the backup-recovery path end-to-end:
 * arm a target chunk, modify a block in it to mark it dirty, wait for the next
 * save pass, and observe the recovery in logs.
 */
public final class CmeDebugTrigger {

    private static final AtomicReference<ChunkPos> ARMED_TARGET = new AtomicReference<>();

    private CmeDebugTrigger() {}

    /**
     * Arms the trigger for a specific chunk. The next save attempt for that
     * chunk will throw a synthetic CME from inside the save mixin, simulating
     * the production race condition deterministically.
     */
    public static void arm(ChunkPos pos) {
        ARMED_TARGET.set(pos);
    }

    /**
     * Clears any armed target without firing.
     */
    public static void disarm() {
        ARMED_TARGET.set(null);
    }

    /**
     * Returns the currently armed target, or null if no chunk is armed.
     * Read-only — does not consume the trigger.
     */
    public static ChunkPos getArmed() {
        return ARMED_TARGET.get();
    }

    /**
     * Atomically tests whether the given chunk pos is the armed target and, if
     * so, consumes the trigger (disarms it) and returns true. Otherwise returns
     * false and leaves the trigger state unchanged. The atomic compare-and-set
     * guarantees exactly-one firing even if multiple saves race on the same chunk.
     */
    public static boolean consumeIfArmed(ChunkPos pos) {
        ChunkPos current = ARMED_TARGET.get();
        if (current == null || !current.equals(pos)) return false;
        return ARMED_TARGET.compareAndSet(current, null);
    }
}
