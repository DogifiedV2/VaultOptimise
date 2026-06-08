package com.dog.vaultoptimise.debug;

import net.minecraft.nbt.CompoundTag;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ConcurrentModificationException;

/**
 * Synthetic CompoundTag whose write method first emits a few bytes and then
 * throws ConcurrentModificationException, faithfully reproducing the production
 * failure mode: the region file's chunk sector receives a truncated payload
 * (because try-with-resources still commits the buffered output stream on close)
 * and a CME propagates up the save call stack.
 *
 * Used only by the debug harness via CmeDebugTrigger. Never appears in the
 * normal save path.
 */
public final class CmeInjectingCompoundTag extends CompoundTag {

    /**
     * Writes a single junk byte to ensure the underlying region sector receives
     * some content, then throws CME. The few committed bytes are enough to make
     * a subsequent vanilla read fail with EOFException, mirroring the symptoms
     * observed in the original IO-Worker error.
     */
    @Override
    public void write(DataOutput output) throws IOException {
        output.writeByte(99);
        throw new ConcurrentModificationException(
                "VaultOptimise debug: synthetic CME after partial write — used to verify backup recovery");
    }
}
