package com.dog.vaultoptimise.mixin.automation.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.InterestManager;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;

@Pseudo
@Mixin(targets = "appeng.me.service.StorageService", remap = false)
public abstract class StorageServiceWatcherDiffMixin {
    @Shadow(remap = false)
    @Final
    private InterestManager<?> interestManager;

    @Redirect(method = "updateCachedStacks()V",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/KeyCounter;iterator()Ljava/util/Iterator;", ordinal = 0),
            remap = false, require = 1, expect = 1, allow = 1)
    private Iterator<Object2LongMap.Entry<AEKey>> vaultoptimise$skipUnwatchedChanges(KeyCounter currentStacks) {
        // This first iterator follows enumeration. The later private-amount refresh must still run.
        return this.interestManager.isEmpty() ? ObjectIterators.emptyIterator() : currentStacks.iterator();
    }

    @Redirect(method = "updateCachedStacks()V",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectSet;iterator()Lit/unimi/dsi/fastutil/objects/ObjectIterator;"),
            remap = false, require = 1, expect = 1, allow = 1)
    private ObjectIterator<Object2LongMap.Entry<AEKey>> vaultoptimise$skipUnwatchedRemovals(
            ObjectSet<Object2LongMap.Entry<AEKey>> previousAmounts) {
        // isEmpty includes both key-specific and watch-all listeners; do not cache their state.
        return this.interestManager.isEmpty() ? ObjectIterators.emptyIterator() : previousAmounts.iterator();
    }
}
