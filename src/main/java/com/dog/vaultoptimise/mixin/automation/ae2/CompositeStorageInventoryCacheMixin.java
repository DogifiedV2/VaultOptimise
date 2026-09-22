package com.dog.vaultoptimise.mixin.automation.ae2;

import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.me.storage.CompositeStorage$InventoryCache", remap = false)
public abstract class CompositeStorageInventoryCacheMixin {
    @Shadow(remap = false)
    private KeyCounter frontBuffer;

    @Inject(method = "update()Z",
            at = @At(value = "CONSTANT", args = "intValue=1"),
            cancellable = true, remap = false, require = 2, expect = 2, allow = 2)
    private void vaultoptimise$finishAfterFirstDifference(CallbackInfoReturnable<Boolean> result) {
        // In AE2 11.7.6, both constants set changed=true after the complete buffer rebuild.
        this.frontBuffer.removeZeros();
        result.setReturnValue(true);
    }
}
