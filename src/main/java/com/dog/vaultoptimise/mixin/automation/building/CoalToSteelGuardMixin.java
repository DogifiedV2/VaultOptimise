package com.dog.vaultoptimise.mixin.automation.building;

import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mcreator.buildingmod.procedures.CoalToSteelProcedureProcedure", remap = false)
public abstract class CoalToSteelGuardMixin {
    @Inject(method = "onRightClickBlock(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1, expect = 1)
    private static void vaultoptimise$skipUnrelatedBlocks(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.getHand() != event.getPlayer().getUsedItemHand() || event.getWorld().isClientSide()) {
            return;
        }
        if (!event.getWorld().getBlockState(event.getPos()).is(Blocks.IRON_BLOCK)) {
            // Skip this handler's config I/O, not the interaction event or other listeners.
            ci.cancel();
        }
    }
}
