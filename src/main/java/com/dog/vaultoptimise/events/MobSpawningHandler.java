package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.commands.LagCommands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MobSpawningHandler {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final int SPAWN_INTERVAL_MINUTES = 5;
    private static final int SPAWN_DURATION_SECONDS = 5;
    private static boolean isCurrentlyEnabled = false;
    private static boolean hasTriggeredForNight = false;
    private static long lastDayChecked = -1;

    public static void startSpawningSystem() {
        // Regular interval spawning
        // scheduler.scheduleAtFixedRate(() -> {
        //            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        //            if (server != null) {
        //                enableAndDisableSpawning(server);
        //            }
        //        }, 0, SPAWN_INTERVAL_MINUTES, TimeUnit.MINUTES);

        // Night time check
        scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                if (overworld != null) {
                    long timeOfDay = overworld.getDayTime() % 24000L;
                    boolean isNightTime = timeOfDay >= 13000 && timeOfDay <= 23000;
                    boolean isDawn = timeOfDay >= 0 && timeOfDay <= 100;

                    // Check if it's a new night
                    if (isDawn) {
                        hasTriggeredForNight = false;
                    }

                    if (isNightTime && !hasTriggeredForNight) {
                        hasTriggeredForNight = true;
                        VaultOptimise.logInfo("Night has started - Triggering spawn cycle");
                        enableAndDisableSpawning(server);
                    } else if (!isNightTime) {
                        hasTriggeredForNight = false;
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private static void enableAndDisableSpawning(MinecraftServer server) {

        if (!LagCommands.extremeMode) return;

        if (!isCurrentlyEnabled) {
            server.getCommands().performCommand(
                    server.createCommandSourceStack(),
                    "gamerule doMobSpawning true"
            );
            isCurrentlyEnabled = true;
            VaultOptimise.logInfo("Enabled mob spawning");

            scheduler.schedule(() -> {
                server.getCommands().performCommand(
                        server.createCommandSourceStack(),
                        "gamerule doMobSpawning false"
                );
                isCurrentlyEnabled = false;
                VaultOptimise.logInfo("Disabled mob spawning");
            }, SPAWN_DURATION_SECONDS, TimeUnit.SECONDS);
        }
    }
}