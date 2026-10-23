package com.dog.vaultoptimise.mixin;

import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DistanceManager.class)
public interface DistanceManagerInvoker {
    @Invoker("updatePlayerTickets")
    void vaultOptimise$updatePlayerTickets(int viewDistance);
}
