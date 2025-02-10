package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerChunkCache.class})
public class ServerChunkCacheMixin {

    private static boolean saveTriggered = false;
    private static int currentSaveCycle = 0;

    @Inject(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z",
            shift = At.Shift.AFTER), cancellable = true)
    public void onSaveAllChunks(boolean force, CallbackInfo ci) {
        if (force) {
            return;
        }

        ci.cancel();

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        int saveCycle = server.getTickCount() / 200;

        if (saveCycle != currentSaveCycle) {
            saveTriggered = false;
            currentSaveCycle = saveCycle;
        }

        if (saveTriggered) {
            return;
        }

        saveTriggered = true;

        if (ServerConfig.CONFIG_VALUES.debugLogging.get()) {
            VaultOptimise.LOGGER.info("A triggered chunk save has been canceled.");
        }

        server.getPlayerList().saveAll();
        VaultOptimise.LOGGER.info("Player data saved.");
    }
}

