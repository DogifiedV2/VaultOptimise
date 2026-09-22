package com.dog.vaultoptimise.mixin.automation.drawers;

import com.dog.vaultoptimise.integration.automation.DrawerInsertionSupport;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.capabilities.DrawerItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.capabilities.DrawerItemHandler", remap = false)
public abstract class DrawerItemHandlerMixin implements DrawerInsertionSupport {
    @Shadow(remap = false)
    private IDrawerGroup group;

    @Override
    public boolean vaultoptimise$supportsBulkInsertion() {
        return ((Object) this).getClass() == DrawerItemHandler.class && group instanceof DrawerInsertionSupport support
                && support.vaultoptimise$supportsBulkInsertion();
    }
}
