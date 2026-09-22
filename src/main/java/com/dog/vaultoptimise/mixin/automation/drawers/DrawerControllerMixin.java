package com.dog.vaultoptimise.mixin.automation.drawers;

import com.dog.vaultoptimise.integration.automation.DrawerInsertionSupport;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.FractionalDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.StandardDrawerGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController", remap = false)
public abstract class DrawerControllerMixin implements DrawerInsertionSupport {
    @Shadow(remap = false)
    protected int[] drawerSlots;

    @Shadow(remap = false)
    protected abstract IDrawerGroup getGroupForDrawerSlot(int slot);

    @Unique
    private boolean vaultoptimise$bulkInsertionSupported;

    @Inject(method = "resetCache", at = @At("HEAD"), remap = false, require = 1, expect = 1)
    private void vaultoptimise$invalidateBulkInsertionSupport(CallbackInfo ci) {
        vaultoptimise$bulkInsertionSupported = false;
    }

    @Inject(method = "rebuildPrimaryLookup", at = @At("RETURN"), remap = false, require = 1, expect = 1)
    private void vaultoptimise$classifyDrawerGroups(CallbackInfo ci) {
        vaultoptimise$bulkInsertionSupported = false;
        if (((Object) this).getClass() != TileEntityController.class) {
            return;
        }

        // Classify once per existing topology-cache rebuild, not once per inserted stack.
        for (int slot : drawerSlots) {
            IDrawerGroup group = getGroupForDrawerSlot(slot);
            if (!(group instanceof StandardDrawerGroup || group instanceof FractionalDrawerGroup)
                    || !(group instanceof DrawerInsertionSupport support)
                    || !support.vaultoptimise$supportsBulkInsertion()) {
                return;
            }
        }
        vaultoptimise$bulkInsertionSupported = true;
    }

    @Override
    public boolean vaultoptimise$supportsBulkInsertion() {
        return ((Object) this).getClass() == TileEntityController.class && vaultoptimise$bulkInsertionSupported;
    }
}
