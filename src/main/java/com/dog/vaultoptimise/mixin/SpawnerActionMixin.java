package com.dog.vaultoptimise.mixin;

import iskallia.ispawner.world.spawner.SpawnerAction;
import iskallia.ispawner.world.spawner.SpawnerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(SpawnerAction.class)
public class SpawnerActionMixin {

    @Inject(method = "applyEggOverride",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V",
                    shift = At.Shift.BEFORE),
            locals = org.spongepowered.asm.mixin.injection.callback.LocalCapture.CAPTURE_FAILHARD)
    private void injectSpawnerNBT(Level world, ItemStack stack, SpawnerContext context, CallbackInfoReturnable<Boolean> cir, BlockState state, EntityType type, BlockPos pos, Entity entity) {
        if (entity != null) {
            boolean isVault = entity.getLevel().dimension().location().getPath().contains("vault");
            if (!isVault) {
                CompoundTag nbt = entity.getPersistentData();
                nbt.putBoolean("spawner", true);
            }
        }
    }

    @Inject(method = "applyEggOverride",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;",
                    shift = At.Shift.AFTER), cancellable = true)
    private void denyAlexMobs(Level world, ItemStack stack, SpawnerContext context, CallbackInfoReturnable<Boolean> cir) {
        Item entityItem = stack.getItem();
        String registryName = entityItem.getRegistryName().toString();

        if (registryName.startsWith("alexsmobs") || registryName.startsWith("eco")) {
            if (!registryName.contains("flutter")) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

}
