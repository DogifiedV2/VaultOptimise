package com.dog.vaultoptimise.mixin.decocraft;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.integration.decocraft.DecocraftSourceDedup;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.razz.decocraft.common.ModuleBlocks", remap = false)
public abstract class ModuleBlocksDedupScopeMixin {
    @Inject(method = "register(Lnet/minecraftforge/registries/IForgeRegistry;)V", at = @At("HEAD"),
            remap = false, require = 1, expect = 1)
    private static void vaultoptimise$begin(IForgeRegistry<?> registry, CallbackInfo callback) {
        DecocraftSourceDedup.begin();
    }
    @Inject(method = "register(Lnet/minecraftforge/registries/IForgeRegistry;)V", at = @At("RETURN"),
            remap = false, require = 1, expect = 1)
    private static void vaultoptimise$finish(IForgeRegistry<?> registry, CallbackInfo callback) {
        VaultOptimise.LOGGER.info("Decocraft texture source dedup: {}", DecocraftSourceDedup.finish());
    }
}
