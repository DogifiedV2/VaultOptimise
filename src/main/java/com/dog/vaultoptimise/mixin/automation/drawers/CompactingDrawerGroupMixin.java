package com.dog.vaultoptimise.mixin.automation.drawers;

import com.dog.vaultoptimise.integration.automation.DrawerInsertionSupport;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersComp;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersComp$GroupData", remap = false)
public abstract class CompactingDrawerGroupMixin implements DrawerInsertionSupport {
    @Shadow(remap = false)
    @Final
    private TileEntityDrawersComp this$0;

    @Override
    public boolean vaultoptimise$supportsBulkInsertion() {
        return getClass().getName().equals("com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersComp$GroupData")
                && this$0.getClass() == TileEntityDrawersComp.Slot3.class;
    }
}
