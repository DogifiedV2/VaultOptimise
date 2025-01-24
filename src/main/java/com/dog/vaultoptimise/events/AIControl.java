package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.List;

public class AIControl {

    private static int spawnedMobsCount = 0;
    private static int mobsAIdisabled = 0;
    private static int mobsAIenabled = 0;

    public static int getSpawnedMobsCount() {
        return spawnedMobsCount;
    }

    public static int getMobsAIdisabled() {
        return mobsAIdisabled;
    }

    public static int getMobsAIenabled() {
        return mobsAIenabled;
    }


    @SubscribeEvent
    public static void onMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getEntity() instanceof Mob mob) {

            if (event.getSpawnReason() == MobSpawnType.NATURAL) {
                CompoundTag nbt = mob.getPersistentData();
                nbt.putString("CustomSpawnReason", event.getSpawnReason().name());
                mob.setCanPickUpLoot(false);
            }

            if (event.getSpawnReason() == MobSpawnType.SPAWNER) {
                mob.setNoAi(true);
            } else if(!isPlayerNearby(mob)) {
                if (mob.isNoAi()) { return; }
                mob.setNoAi(true);
            }
        }
    }

    private static final int CHECK_INTERVAL = 100; // 5 seconds

    @SubscribeEvent
    public static void onMobUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.getLevel().dimension().equals(Level.OVERWORLD)) {

            if (mob.tickCount % CHECK_INTERVAL == 0) {
                boolean playerNearby = isPlayerNearby(mob);

                CompoundTag nbt = mob.getPersistentData();

       if (nbt.contains("CustomSpawnReason")) {
                if (playerNearby) {
                    if (!mob.isNoAi()) {
                        return;
                    }
                    mob.setNoAi(false);
                } else {
                    if (mob.isNoAi()) { return; }
                    mob.setNoAi(true);
                }
               }
            }
        }
    }

    @SubscribeEvent
    public static void onMobItemCheck(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.getLevel().dimension().equals(Level.OVERWORLD)) {
            ItemStack mainHandItem = mob.getMainHandItem();
            ItemStack offHandItem = mob.getOffhandItem();

            if (!mainHandItem.isEmpty() || !offHandItem.isEmpty()) {
                CompoundTag nbt = mob.getPersistentData();

                if (nbt.contains("CustomSpawnReason")) {
                mob.discard();
            }
            }
        }
    }

    private static final double ACTIVATION_RADIUS = ServerConfig.CONFIG_VALUES.ActivationRadius.get();
    private static final double VERTICAL_RADIUS = ServerConfig.CONFIG_VALUES.ActivationHeight.get();

    private static boolean isPlayerNearby(Mob mob) {
        List<? extends Player> players = mob.getLevel().players();
        for (Player player : players) {
            double dx = player.getX() - mob.getX();
            double dz = player.getZ() - mob.getZ();
            double horizontalDistanceSqr = dx * dx + dz * dz;

            double dy = Math.abs(player.getY() - mob.getY());

            // Verify if within both horizontal and vertical radius
            if (horizontalDistanceSqr <= ACTIVATION_RADIUS * ACTIVATION_RADIUS && dy <= VERTICAL_RADIUS) {
                return true;
            }
        }
        return false;
    }





}