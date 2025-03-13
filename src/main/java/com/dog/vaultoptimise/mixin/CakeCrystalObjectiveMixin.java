package com.dog.vaultoptimise.mixin;

import com.dog.vaultoptimise.VaultOptimise;
import iskallia.vault.core.data.adapter.Adapters;
import iskallia.vault.core.world.roll.IntRoll;
import iskallia.vault.item.crystal.objective.CakeCrystalObjective;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CakeCrystalObjective.class)
public class CakeCrystalObjectiveMixin {

    @Shadow
    protected IntRoll target;

    @Shadow
    private float objectiveProbability;

    /**
     * @author dogv2
     * @reason bug fix
     */
    @Overwrite(remap = false)
    public void readNbt(CompoundTag nbt) {
        this.target = (IntRoll) Adapters.INT_ROLL.readNbt(nbt.getCompound("target")).orElse((IntRoll) null);
        this.objectiveProbability = 1.0F;
    }
}
