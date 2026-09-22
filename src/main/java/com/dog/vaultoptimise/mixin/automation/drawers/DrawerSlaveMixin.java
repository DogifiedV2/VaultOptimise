package com.dog.vaultoptimise.mixin.automation.drawers;

import com.dog.vaultoptimise.integration.automation.DrawerInsertionSupport;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntitySlave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntitySlave", remap = false)
public abstract class DrawerSlaveMixin implements DrawerInsertionSupport {
    @Shadow(remap = false)
    public abstract TileEntityController getController();

    @Override
    public boolean vaultoptimise$supportsBulkInsertion() {
        if (((Object) this).getClass() != TileEntitySlave.class) {
            return false;
        }
        return getController() instanceof DrawerInsertionSupport support
                && support.vaultoptimise$supportsBulkInsertion();
    }
}
