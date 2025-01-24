package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.config.ServerConfig;
import com.dog.vaultoptimise.saving.AutoSaveHandler;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DynamicGraphMinFixedPoint.class)
public abstract class MixinDynamicGraphMinFixedPoint {

    @Shadow
    @Final
    private LongLinkedOpenHashSet[] queues;

    @Shadow
    private int firstQueuedLevel;

    @Shadow
    protected abstract void checkFirstQueuedLevel(int p_75547_);

    @Shadow
    @Final
    private int levelCount;

    @Inject(method = "runUpdates", at = @At("HEAD"), cancellable = true)
    private void pauseDuringSave(int p_75589_, CallbackInfoReturnable<Integer> cir) {
        if (AutoSaveHandler.currentlySaving && ServerConfig.CONFIG_VALUES.LightUpdates.get()) {
            cir.setReturnValue(p_75589_);
            cir.cancel();
        }
    }
}

