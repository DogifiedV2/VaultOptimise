package com.dog.vaultoptimise.commands;

import com.dog.vaultoptimise.saving.AutoSaveHandler;
import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import iskallia.vault.block.DungeonDoorBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.dog.vaultoptimise.events.LockdownHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber
public class MainCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean vaultsLocked = false;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("vaultoptimise")
                .requires(source -> source.hasPermission(2)) // Requires permission level 2
                .then(Commands.literal("lockdown")
                        .executes(MainCommand::toggleLockdown))
                .then(Commands.literal("lockvaults")
                        .executes(MainCommand::lockVaults))
                .then(Commands.literal("saves")
                        .then(Commands.literal("manualsave")
                                .executes(MainCommand::executeManualSave))
                        .then(Commands.literal("toggleautosave")
                                .executes(MainCommand::toggleAutoSave))
                        .then(Commands.literal("togglesavelogs")
                                .executes(MainCommand::toggleSaveLogs)))
        );
    }

    // ** // ** // ** //
    // ** // ** // ** //
    // ** COMMANDS ** //
    // ** // ** // ** //
    // ** // ** // ** //

    private static int lockVaults(CommandContext<CommandSourceStack> context) {
        vaultsLocked = !vaultsLocked;
        context.getSource().sendSuccess(
                new TextComponent("Vaults have been " + (vaultsLocked ? "locked" : "unlocked") + "."),
                true
        );
        return Command.SINGLE_SUCCESS;
    }


    private static int toggleAutoSave(CommandContext<CommandSourceStack> context) {
        boolean currentState = AutoSaveHandler.isAutosaveEnabled();
        AutoSaveHandler.setAutosaveEnabled(!currentState);
        if (context.getSource().getEntity() instanceof Player player) {
            player.sendMessage(new TextComponent("Autosave has been " + (currentState ? "disabled" : "enabled") + "."), player.getUUID());
            player.sendMessage(new TextComponent("Note: this is only applied until the server restarts. Please use the configuration to permanently toggle saving."), player.getUUID());
        }
        return Command.SINGLE_SUCCESS;
    }

    public static boolean ChunkLogs = true;
    private static int toggleSaveLogs(CommandContext<CommandSourceStack> context) {
        ChunkLogs = !ChunkLogs;
        if (context.getSource().getEntity() instanceof Player player) {
            player.sendMessage(new TextComponent("Logs have been " + (ChunkLogs ? "disabled" : "enabled") + "."), player.getUUID());
        }
        return Command.SINGLE_SUCCESS;
    }


    private static int executeManualSave(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        com.dog.vaultoptimise.VaultOptimise.sendMessageToOppedPlayers("Manual Save starting...");

        for (ServerLevel level : server.getAllLevels()) {
            // Only save the main 3 minecraft dimensions
            if (level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER || level.dimension() == Level.END) {
                String dimensionName = level.dimension().location().toString();
                LOGGER.info("Saving chunks for dimension: " + dimensionName);

                long levelStartTime = System.nanoTime(); // Record the start time for this dimension
                try {
                    level.save(null, false, false);
                } catch (Exception e) {
                    LOGGER.debug("Error while saving dimension: " + e.getMessage());
                    com.dog.vaultoptimise.VaultOptimise.sendMessageToOppedPlayers("An error occurred while attempting to save.");
                    e.printStackTrace();
                }

                long levelEndTime = System.nanoTime(); // Record end time for this dimension
                long levelDuration = TimeUnit.NANOSECONDS.toMillis(levelEndTime - levelStartTime);
                LOGGER.info("Completed saving dimension: " + dimensionName + " in " + levelDuration + " ms");
            } else {
                LOGGER.info("Skipping dimension: " + level.dimension().location().toString());

            }
        }

        server.getPlayerList().saveAll();
        com.dog.vaultoptimise.VaultOptimise.sendMessageToOppedPlayers("Manual Save completed.");
        return Command.SINGLE_SUCCESS;

    }

    private static int toggleLockdown(CommandContext<CommandSourceStack> context) {
        boolean currentState = LockdownHandler.isLockdownEnabled();
        LockdownHandler.setLockdownEnabled(!currentState);

        context.getSource().sendSuccess(
                new TextComponent("Lockdown has been " + (!currentState ? "enabled" : "disabled") + "."),
                true
        );

        return Command.SINGLE_SUCCESS;
    }

}
