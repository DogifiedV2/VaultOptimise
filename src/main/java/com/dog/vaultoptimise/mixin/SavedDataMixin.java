package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.config.ServerConfig;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;

@Mixin({SavedData.class})
public class SavedDataMixin {

    @Redirect(method = "save(Ljava/io/File;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
    private void worldSaveFiles(CompoundTag compoundTag, File file) {
        long startTime = System.nanoTime();

        Util.ioPool().submit(() -> {
            try {
                NbtIo.writeCompressed(compoundTag, file);
                long endTime = System.nanoTime();
                long durationMillis = (endTime - startTime) / 1_000_000;

                long fileSize = file.exists() ? file.length() : -1;
                if (fileSize > 10 && ServerConfig.CONFIG_VALUES.debugLogging.get()) {
                    VaultOptimise.LOGGER.info("Saved file: {} | Size: {} bytes | Time taken: {} ms",
                            file.getName(), fileSize, durationMillis);
                }
            } catch (IOException ioexception) {
                VaultOptimise.LOGGER.error("Could not save file: {}", file.getName(), ioexception);
            }
        });
    }


}
