package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;

public class AIControl {

    private static final int CHECK_INTERVAL = 100;
    private static final double ACTIVATION_RADIUS = ServerConfig.CONFIG_VALUES.ActivationRadius.get();
    private static final double VERTICAL_RADIUS = ServerConfig.CONFIG_VALUES.ActivationHeight.get();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!isValidMob(event.getEntity())) return;
        Mob mob = (Mob) event.getEntity();

        if (event.getSpawnReason() == MobSpawnType.SPAWNER) {
            mob.setNoAi(true);
        }

        if (event.getSpawnReason() == MobSpawnType.NATURAL) {
            CompoundTag nbt = mob.getPersistentData();
            mob.setCanPickUpLoot(false);
            nbt.putString("CustomSpawnReason", event.getSpawnReason().name());
        }
    }

    @SubscribeEvent
    public static void onMobUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!isValidMob(event.getEntity())) return;
        Mob mob = (Mob) event.getEntity();

        if (mob.tickCount % CHECK_INTERVAL != 0) return;
        CompoundTag nbt = mob.getPersistentData();

        if (!nbt.contains("CustomSpawnReason")) return;
        boolean playerNearby = isPlayerNearby(mob);
        mob.setNoAi(!playerNearby);
    }

    @SubscribeEvent
    public static void onMobItemCheck(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.getLevel().dimension() != Level.OVERWORLD) return;

        CompoundTag nbt = mob.getPersistentData();
        if (!nbt.contains("CustomSpawnReason")) return;

        if (!mob.getMainHandItem().isEmpty() || !mob.getOffhandItem().isEmpty()) {
            mob.discard();
        }
    }

    private static boolean isPlayerNearby(Mob mob) {
        for (Player player : mob.getLevel().players()) {
            if (Math.abs(player.getY() - mob.getY()) > VERTICAL_RADIUS) continue;
            if (player.distanceToSqr(mob) <= ACTIVATION_RADIUS * ACTIVATION_RADIUS) return true;
        }
        return false;
    }

    private static boolean isValidMob(Object entity) {
        return (entity instanceof Mob mob);
    }
}
