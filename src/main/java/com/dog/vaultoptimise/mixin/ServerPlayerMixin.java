package com.dog.vaultoptimise.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.dog.vaultoptimise.saving.VaultTracker.addCompletedVault;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    public void onDisplayClientMessage(Component component, boolean actionBar, CallbackInfo ci) {
        String message = component.getString();
        if (message.contains("Teleporting back in 10")) {
            addCompletedVault();
        }
    }
}