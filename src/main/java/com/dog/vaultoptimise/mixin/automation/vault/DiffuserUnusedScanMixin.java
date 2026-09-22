package com.dog.vaultoptimise.mixin.automation.vault;

import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "iskallia.vault.block.entity.VaultDiffuserUpgradedTileEntity", remap = false)
public abstract class DiffuserUnusedScanMixin {
    @Redirect(method = "insertStack(Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/lang/Object;Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Liskallia/vault/block/entity/VaultDiffuserUpgradedTileEntity;isEmpty(Lnet/minecraftforge/items/IItemHandler;)Z"),
            remap = false, require = 1, expect = 1, allow = 1)
    private static boolean vaultoptimise$skipUnusedInventoryScan(IItemHandler inventory) {
        // The exact target stores this result in an unused local. Transfer/accounting stays unchanged.
        return false;
    }
}
