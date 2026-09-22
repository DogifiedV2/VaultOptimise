package com.dog.vaultoptimise.mixin.automation.building;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mcreator.buildingmod.procedures.SteelToCoalProcedureProcedure", remap = false)
public abstract class SteelToCoalGuardMixin {
    @Unique
    private static final ResourceLocation vaultoptimise$steelBlockId =
            new ResourceLocation("davebuildingmod", "steel_block");

    @Inject(method = "onRightClickBlock(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1, expect = 1)
    private static void vaultoptimise$skipUnrelatedBlocks(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.getHand() != event.getPlayer().getUsedItemHand() || event.getWorld().isClientSide()) {
            return;
        }
        if (!ForgeRegistries.BLOCKS.containsKey(vaultoptimise$steelBlockId)) {
            return;
        }

        Block steelBlock = ForgeRegistries.BLOCKS.getValue(vaultoptimise$steelBlockId);
        if (steelBlock != null && event.getWorld().getBlockState(event.getPos()).getBlock() != steelBlock) {
            // Skip this handler's config I/O, not the interaction event or other listeners.
            ci.cancel();
        }
    }
}
