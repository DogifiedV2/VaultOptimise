package com.dog.vaultoptimise.saving;

import com.dog.vaultoptimise.config.ServerConfig;
import com.mojang.logging.LogUtils;
import iskallia.vault.item.BoosterPackItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.dog.vaultoptimise.saving.VaultTracker.*;

public class AutoSaveHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean autosaveEnabled = true;
    private static boolean autosaveCrash = false;
    public static boolean currentlySaving = false;

    private static long lastAutosaveTime = System.nanoTime();

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(1);


    public static boolean isAutosaveEnabled() {
        return autosaveEnabled;
    }

    public static void setAutosaveEnabled(boolean enabled) {
        autosaveEnabled = enabled;
        LOGGER.info("Autosave has been " + (enabled ? "enabled" : "disabled") + ".");
    }

    private void logInGame(String message) {
        if (ServerConfig.CONFIG_VALUES.AutoSaveLogs.get()) {
            com.dog.vaultoptimise.VaultOptimise.sendMessageToOppedPlayers(message);
        }
    }

    @SubscribeEvent
    public void onCustomAutoSave(CustomAutoSaveEvent event) {
        if (!autosaveEnabled) {
            LOGGER.info("Autosave is currently disabled. Skipping autosave process.");
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long currentTime = System.nanoTime();
        long timeSinceLastSave = TimeUnit.NANOSECONDS.toMinutes(currentTime - lastAutosaveTime);

        if (timeSinceLastSave > 10) {
            LOGGER.warn("Last autosave was more than 10 minutes ago. Forcing autosave.");
            cleanCompletedVaults();
            performAutosave(server);
            return;
        }

        if (hasCompletedVaults()) {
            LOGGER.warn("User exiting vault. Delaying save.");
            executor.schedule(() -> delayedAutosave(server), 16, TimeUnit.SECONDS);
        } else {
            performAutosave(server);
        }
    }


    private void delayedAutosave(MinecraftServer server) {
        if (hasCompletedVaults()) {
            LOGGER.warn("User exiting vault. Delaying save.");
            executor.schedule(() -> delayedAutosave(server), 16, TimeUnit.SECONDS);
        } else {
            performAutosave(server);
        }
    }

    private void performAutosave(MinecraftServer server) {
        server.getCommands().performCommand(
                server.createCommandSourceStack(),
                "save-off"
        );
        LOGGER.info("Autosave started...");
        logInGame("Starting autosave...");
        currentlySaving = true;
        long startTime = System.nanoTime();

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD) {
                String dimensionName = level.dimension().location().toString();
                LOGGER.info("Saving chunks for dimension: " + dimensionName);

                long levelStartTime = System.nanoTime();
                try {
                     level.save(null, false, false);
                   // server.saveAllChunks(false, true, true);
                } catch (ArrayIndexOutOfBoundsException e) {
                    LOGGER.error("Critical error: ArrayIndexOutOfBoundsException occurred during autosave: " + e.getMessage(), e);
                    logInGame("Critical error during autosave. Autosave aborted to prevent crash.");
                    currentlySaving = false;
                    autosaveCrash = true;
                } catch (Exception e) {
                    LOGGER.debug("Error while saving dimension: " + e.getMessage(), e);
                    logInGame("An error occurred while attempting to autosave.");
                    currentlySaving = false;
                    autosaveCrash = true;
                    e.printStackTrace();
                }

                long levelEndTime = System.nanoTime();
                long levelDuration = TimeUnit.NANOSECONDS.toMillis(levelEndTime - levelStartTime);
                LOGGER.info("Completed saving dimension: " + dimensionName + " in " + levelDuration + " ms");
            } else {
                LOGGER.info("Skipping dimension: " + level.dimension().location().toString());
            }
        }

        savePlayerData(server);

        long endTime = System.nanoTime();
        long totalDuration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        LOGGER.info("Autosave completed in " + totalDuration + " ms.");
        currentlySaving = false;
        lastAutosaveTime = System.nanoTime();
        logInGame("Autosave completed in " + totalDuration + " ms.");
        playerQueue.clear();
        server.getCommands().performCommand(
                server.createCommandSourceStack(),
                "save-off"
        );
    }

    //

    public void startAutoSaveTask() {
        currentlySaving = false;
        executor.scheduleAtFixedRate(() -> MinecraftForge.EVENT_BUS.post(new CustomAutoSaveEvent()),
                120, 300, TimeUnit.SECONDS);

        monitorExecutor.scheduleAtFixedRate(this::checkLastAutoSave, 180, 300, TimeUnit.SECONDS);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        server.getCommands().performCommand(
                server.createCommandSourceStack(),
                "save-off"
        );
    }

    private void savePlayerData(MinecraftServer server) {
        long startTime = System.nanoTime();
        server.getPlayerList().saveAll();
        LOGGER.info("Player data saved in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
    }

    private void checkLastAutoSave() {
        long currentTime = System.nanoTime();
        long timeSinceLastSave = TimeUnit.NANOSECONDS.toMinutes(currentTime - lastAutosaveTime);

        if (!isAutosaveEnabled()) return;
        if (timeSinceLastSave >= 7 || autosaveCrash) {
            LOGGER.info("Autosave task appears to have stopped. Restarting autosave scheduler...");
            logInGame("Attempting to restart autosave task.");

            // Shut down the primary autosave task and restart it
            LOGGER.info("Attempting to shut down primary autosave executor.");
            executor.shutdownNow();
            LOGGER.info("Shutdown signal sent to executor.");

            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.info("Primary autosave executor did not shut down gracefully.");
                    logInGame("Something went wrong while restarting the autosave process.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("Interrupted while shutting down the primary autosave executor. Please report the error.");
                logInGame("Process was interrupted while restarting. Please report the error.");
            }

            LOGGER.info("Re-initializing the executor.");
            executor = Executors.newScheduledThreadPool(1);

            LOGGER.info("Scheduling new autosave task.");
            executor.scheduleAtFixedRate(() -> MinecraftForge.EVENT_BUS.post(new CustomAutoSaveEvent()),
                    0, 300, TimeUnit.SECONDS);
            LOGGER.info("Primary autosave scheduler restarted.");
            autosaveCrash = false;
            logInGame("Autosave scheduler restarted.");
        }
    }

    public final static Queue<ServerPlayer> playerQueue = new LinkedList<>();
    public static boolean addPlayerToQueue(ServerPlayer player) {
        if (playerQueue.contains(player)) {
            return false;
        } else {
            playerQueue.add(player);
            return true;
        }
    }

    //@SubscribeEvent
    public void onAnyEvent(Event event) {
        String name = event.getClass().getName();
        if (name.contains("ChunkDataEvent") || name.contains("iskallia") || name.contains("PlayerDestroyItemEvent")) {
            com.dog.vaultoptimise.VaultOptimise.LOGGER.info(name);
        }
    }


}
