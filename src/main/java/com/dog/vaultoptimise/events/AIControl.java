package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.config.ServerConfig;
import com.google.common.collect.ImmutableMultimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

public class AIControl {

    private static final int CHECK_INTERVAL = 100;
    private static final double ACTIVATION_RADIUS = ServerConfig.CONFIG_VALUES.ActivationRadius.get();
    private static final double VERTICAL_RADIUS = ServerConfig.CONFIG_VALUES.ActivationHeight.get();

    private static final double VAULT_ACTIVATION_RADIUS = ServerConfig.CONFIG_VALUES.VaultActivationRadius.get();


    // Regular overworld spawns
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOverworldMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!isValidMob(event.getEntity())) return;
        PathfinderMob mob = (PathfinderMob) event.getEntity();

        if (mob.getLevel().dimension().location().getPath().contains("vault")) return;
        applySpawnTag(mob);
    }

    private static void applySpawnTag(PathfinderMob mob) {
        if (mob == null || !mob.isAlive()) return;
        CompoundTag nbt = mob.getPersistentData();
        mob.setCanPickUpLoot(false);
        nbt.putString("CustomSpawnReason", mob.getClass().getSimpleName());
    }



    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onVaultMobSpawn(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof Bat) {
            event.setCanceled(true);
            return;
        }

        if (!isValidMob(event.getEntity())) return;
        PathfinderMob mob = (PathfinderMob) event.getEntity();

        CompoundTag nbt = mob.getPersistentData();
        boolean isVault = mob.getLevel().dimension().location().getPath().contains("vault");

        // Disable AI for iSpawner mobs.
        if (!isVault && nbt.contains("spawner")) {
            nbt.putString("CustomSpawnReason", "SPAWNER");
            deleteMobAI(mob);
            return;
        }

        // Only vault mobs from this point.
        if (!isVault) return;

        mob.setCanPickUpLoot(false);
        if (nbt.contains("CustomSpawnReason")) return;
        nbt.putString("CustomSpawnReason", "VAULT");
    }

    @SubscribeEvent
    public static void onMobUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!isValidMob(event.getEntity())) return;
        PathfinderMob mob = (PathfinderMob) event.getEntity();

        CompoundTag nbt = mob.getPersistentData();
        boolean isVault = mob.getLevel().dimension().location().getPath().contains("vault");
        if (!isVault && nbt.contains("spawner")) {
            deleteMobAI(mob);
            return;
        }

        if (mob.tickCount % CHECK_INTERVAL != 0) return;

        if (!nbt.contains("CustomSpawnReason")) {
            nbt.putString("CustomSpawnReason", isVault ? "VAULT" : mob.getClass().getSimpleName());
        }

        // Toggle AI based on player proximity.
        boolean playerNearby = isPlayerNearby(mob);
        mob.setNoAi(!playerNearby);
    }

    @SubscribeEvent
    public static void onMobItemCheck(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;
        if (mob.getLevel().dimension() != Level.OVERWORLD) return;

        CompoundTag nbt = mob.getPersistentData();
        if (!nbt.contains("CustomSpawnReason")) return;

        if (!mob.getMainHandItem().isEmpty() || !mob.getOffhandItem().isEmpty()) {
            mob.discard();
        }
    }

    private static boolean isPlayerNearby(PathfinderMob mob) {
        String dimension = mob.getLevel().dimension().location().getPath();
        double radiusSq = (dimension.contains("vault") ? VAULT_ACTIVATION_RADIUS : ACTIVATION_RADIUS);
        radiusSq *= radiusSq;

        for (Player player : mob.getLevel().players()) {
            double verticalDiff = Math.abs(player.getY() - mob.getY());
            double maxVertical = dimension.contains("vault") ? 50 : VERTICAL_RADIUS;

            if (verticalDiff > maxVertical) { continue; }
            if (player.distanceToSqr(mob) <= radiusSq) return true;
        }

        // If the only reason we failed was Y distance, check shouldKeepAI
        return shouldKeepAI(mob);
    }

    private static boolean shouldKeepAI(PathfinderMob mob) {
        return mob.fallDistance > 0;
    }

    private static boolean isValidMob(Object entity) {
        return (entity instanceof PathfinderMob);
    }

    public static void deleteMobAI(PathfinderMob mob) {
        mob.goalSelector.getAvailableGoals().clear();
        mob.targetSelector.getAvailableGoals().clear();

        mob.getNavigation().stop();
        mob.setSpeed(0);
        mob.xxa = 0;
        mob.zza = 0;
        mob.yHeadRot = mob.getYRot();
    }

}
