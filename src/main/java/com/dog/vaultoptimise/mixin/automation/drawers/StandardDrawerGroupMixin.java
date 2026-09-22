package com.dog.vaultoptimise.mixin.automation.drawers;

import com.dog.vaultoptimise.integration.automation.DrawerInsertionSupport;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersStandard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersStandard$GroupData", remap = false)
public abstract class StandardDrawerGroupMixin implements DrawerInsertionSupport {
    @Shadow(remap = false)
    @Final
    private TileEntityDrawersStandard this$0;

    @Override
    public boolean vaultoptimise$supportsBulkInsertion() {
        Class<?> owner = this$0.getClass();
        // Addon subclasses can reuse GroupData while changing their drawer behavior.
        return getClass().getName().equals("com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersStandard$GroupData")
                && (owner == TileEntityDrawersStandard.Slot1.class
                || owner == TileEntityDrawersStandard.Slot2.class
                || owner == TileEntityDrawersStandard.Slot4.class);
    }
}
