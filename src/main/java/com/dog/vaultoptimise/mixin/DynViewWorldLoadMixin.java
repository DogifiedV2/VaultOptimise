package com.dog.vaultoptimise.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * dynview resets the server view distance to its minimum on every world load. Vaults load a new world each time,
 * so every vault opened shrank everyone's view to the minimum. Only the overworld load at server start resets it.
 */
@Pseudo
@Mixin(targets = "com.dynamic_view.event.EventHandler", remap = false)
public abstract class DynViewWorldLoadMixin {
    @Inject(method = "onWorldLoad", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void vaultOptimise$onlyResetOnOverworldLoad(WorldEvent.Load event, CallbackInfo ci) {
        if (!(event.getWorld() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            ci.cancel();
        }
    }
}
