package com.dog.vaultoptimise.mixin.automation.pipez;

import com.dog.vaultoptimise.integration.automation.DrawerInsertionSupport;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType", remap = false)
public abstract class OrderedDrawerInsertionMixin {
    @Redirect(method = "insertOrdered",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/items/ItemHandlerHelper;insertItem(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"),
            remap = false, require = 1, expect = 1, allow = 1)
    private ItemStack vaultoptimise$insertIntoDrawerGroup(IItemHandler destination, ItemStack stack, boolean simulate) {
        if (!simulate && !stack.isEmpty() && destination instanceof DrawerInsertionSupport support
                && support.vaultoptimise$supportsBulkInsertion()) {
            // Virtual slot zero already tries the whole group. Do not retry its physical slots.
            return destination.insertItem(0, stack, false);
        }
        return ItemHandlerHelper.insertItem(destination, stack, simulate);
    }
}
