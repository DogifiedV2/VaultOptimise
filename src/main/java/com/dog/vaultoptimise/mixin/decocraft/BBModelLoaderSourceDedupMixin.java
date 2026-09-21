package com.dog.vaultoptimise.mixin.decocraft;

import com.razz.decocraft.models.bbmodel.BBModel;
import com.dog.vaultoptimise.integration.decocraft.DecocraftSourceDedup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.Reader;

@Pseudo
@Mixin(targets = "com.razz.decocraft.models.bbmodel.BBModelLoader", remap = false)
public abstract class BBModelLoaderSourceDedupMixin {
    @Inject(method = "loadModel(Ljava/io/Reader;)Lcom/razz/decocraft/models/bbmodel/BBModel;",
            at = @At("RETURN"), remap = false, require = 1, expect = 1)
    private void vaultoptimise$deduplicateSources(Reader reader, CallbackInfoReturnable<BBModel> result) {
        DecocraftSourceDedup.process(result.getReturnValue());
    }
}
