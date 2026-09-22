package com.dog.vaultoptimise.mixin.automation.powah;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.lib.logistics.Redstone;

@Pseudo
@Mixin(targets = "owmii.powah.lib.block.AbstractTileEntity", remap = false)
public abstract class IgnoredRedstoneMixin {
    @Shadow(remap = false)
    public abstract Redstone getRedstoneMode();

    @Inject(method = "checkRedstone()Z", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1, expect = 1)
    private void vaultoptimise$skipIgnoredRedstone(CallbackInfoReturnable<Boolean> result) {
        if (getRedstoneMode() == Redstone.IGNORE) {
            result.setReturnValue(true);
        }
    }
}
