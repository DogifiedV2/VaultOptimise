package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.backup.ChunkBackupManager;
import com.dog.vaultoptimise.debug.CmeDebugTrigger;
import com.dog.vaultoptimise.debug.CmeInjectingCompoundTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ConcurrentModificationException;

/**
 * Wraps the chunk save call inside IOWorker so VaultOptimise can:
 *
 *   1. After a successful save, trigger an asynchronous backup write for the
 *      chunk if it is currently claimed in OPaC.
 *   2. On a save-time CME (the production race condition where a buggy mod
 *      mutates chunk NBT during NbtIo.write iteration), automatically restore
 *      the chunk from its last good backup and retry the write — overwriting
 *      the partially corrupted region sector with valid bytes.
 *   3. Inject a synthetic CME for a single targeted chunk when the debug
 *      trigger is armed, so the recovery path can be verified deterministically.
 *
 * The load-side recovery hook lives in RegionFileStorageMixin instead, because
 * IOWorker.loadAsync's storage.read call is inside a lambda which Mixin's
 * literal-method @Redirect cannot reach reliably.
 */
@Mixin(IOWorker.class)
public abstract class IOWorkerMixin {

    @Inject(method = "close", at = @At("RETURN"))
    private void vaultOptimise$unregisterClosedWorker(CallbackInfo ci) {
        ChunkBackupManager.unregister((IOWorker) (Object) this);
    }

    /**
     * Wraps RegionFileStorage.write inside IOWorker.runStore. Handles three
     * concerns in one redirect: debug CME injection, backup-on-success, and
     * recovery-on-CME. On unrecoverable failure the CME is rethrown so vanilla's
     * outer try/catch in runStore logs it and exceptionally completes the
     * pending CompletableFuture.
     */
    @Redirect(
            method = "runStore",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"
            )
    )
    private void vaultOptimise$wrapWrite(RegionFileStorage storage, ChunkPos pos, CompoundTag data) throws IOException {
        IOWorker worker = (IOWorker) (Object) this;

        // Only let the debug trigger fire for chunk-storage workers. Entity and
        // POI workers also hit this redirect; without this filter they would
        // consume the one-shot trigger first and the actual chunk save would
        // never see the synthetic CME.
        boolean isChunkStorageWorker = ChunkBackupManager.resolveLevel(worker) != null;
        CompoundTag tagToWrite = (isChunkStorageWorker && CmeDebugTrigger.consumeIfArmed(pos))
                ? new CmeInjectingCompoundTag()
                : data;

        try {
            ((RegionFileStorageInvoker) (Object) storage).vaultOptimise$invokeWrite(pos, tagToWrite);
            // Backup is taken from `data` (the original chunk tag), never from
            // `tagToWrite` — tagToWrite may be the synthetic CME stub from the
            // debug harness, which contains no chunk content.
            ChunkBackupManager.onSaveSuccess(worker, pos, data);
        } catch (ConcurrentModificationException cme) {
            boolean recovered = ChunkBackupManager.tryRecoverFromCmeAndRetry(worker, storage, pos, cme);
            if (!recovered) {
                throw cme;
            }
        }
    }
}
