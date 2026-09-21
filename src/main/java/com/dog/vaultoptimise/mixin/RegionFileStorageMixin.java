package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.backup.ChunkBackupManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.DataInput;
import java.io.IOException;
import java.util.Optional;

/**
 * Wraps RegionFileStorage.read so that read failures (typically EOFException
 * from a previously corrupted region sector) automatically fall back to the
 * chunk's backup file when one exists. If no backup is available, the original
 * exception is rethrown so vanilla's chunk regeneration path runs as usual.
 *
 * Implementation note: we capture the active ChunkPos in a thread-local at the
 * head of read() so the redirect around NbtIo.read can perform the backup
 * lookup. The thread-local is overwritten on every read entry, so a stale value
 * left behind by an exceptional exit cannot leak into a subsequent read on the
 * same thread.
 */
@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin {

    private static final ThreadLocal<ChunkPos> CURRENT_READ_POS = new ThreadLocal<>();

    @Inject(method = "close", at = @At("RETURN"))
    private void vaultOptimise$forgetClosedStorage(CallbackInfo ci) {
        ChunkBackupManager.forgetStorage((RegionFileStorage) (Object) this);
    }

    /**
     * Captures the chunk pos being read so the redirect below can look up the
     * correct backup if NbtIo.read throws.
     */
    @Inject(method = "read", at = @At("HEAD"))
    private void vaultOptimise$captureReadPos(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {
        CURRENT_READ_POS.set(pos);
    }

    /**
     * Wraps the NbtIo.read call inside RegionFileStorage.read. On read failure,
     * tries to load a backup for the chunk; returns the backup if one exists,
     * otherwise rethrows the original failure. On success returns the read tag
     * unchanged.
     */
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;read(Ljava/io/DataInput;)Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    private CompoundTag vaultOptimise$wrapNbtRead(DataInput input) throws IOException {
        try {
            return NbtIo.read(input);
        } catch (IOException ioFailure) {
            CompoundTag recovered = vaultOptimise$tryRecoverFromReadFailure();
            if (recovered != null) return recovered;
            throw ioFailure;
        } catch (RuntimeException runtimeFailure) {
            CompoundTag recovered = vaultOptimise$tryRecoverFromReadFailure();
            if (recovered != null) return recovered;
            throw runtimeFailure;
        }
    }

    /**
     * Looks up the backup for the currently-being-read chunk and returns its
     * contents if found, or null otherwise. Extracted so both exception arms
     * of the redirect share identical recovery logic without code duplication.
     */
    private CompoundTag vaultOptimise$tryRecoverFromReadFailure() {
        ChunkPos pos = CURRENT_READ_POS.get();
        if (pos == null) return null;
        Optional<CompoundTag> backup = ChunkBackupManager.tryLoadBackupByStorage(
                (RegionFileStorage) (Object) this, pos);
        return backup.orElse(null);
    }
}
