package com.dog.vaultoptimise;

import com.dog.vaultoptimise.events.DimensionChangeEvent;
import com.dog.vaultoptimise.saving.AutoSaveHandler;
import com.dog.vaultoptimise.events.AIControl;
import com.dog.vaultoptimise.config.ServerConfig;
import com.mojang.logging.LogUtils;
import iskallia.vault.block.ScavengerAltarBlock;
import iskallia.vault.core.vault.objective.OfferingBossObjective;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.task.GodAltarTask;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod("vaultoptimise")
public class VaultOptimise {

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final AutoSaveHandler autoSaveHandler = new AutoSaveHandler();
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "vaultoptimise";

    public static void logInfo(String message) {
        LOGGER.info(message);
    }


    public VaultOptimise() {
        System.out.println("vaultoptimise constructor called.");
        MinecraftForge.EVENT_BUS.addListener(this::onServerStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStop);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        // Commands and config
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.CONFIG);
    }

    private void setup(final FMLCommonSetupEvent event) {

    }

    private void onServerStart(ServerStartingEvent event) {
        logInfo("Loading Events!");

            if (ServerConfig.CONFIG_VALUES.AutoSaves.get()) {
                MinecraftForge.EVENT_BUS.register(new AutoSaveHandler());
                autoSaveHandler.startAutoSaveTask();
                logInfo("Auto Save task started");
            }

            if (ServerConfig.CONFIG_VALUES.MobAIControl.get()) {
                MinecraftForge.EVENT_BUS.register(AIControl.class);
                logInfo("Mob AI Control started");
            }

            if (ServerConfig.CONFIG_VALUES.VaultRaidEffect.get()) {
                MinecraftForge.EVENT_BUS.register(DimensionChangeEvent.class);
                logInfo("Vault Raid fix started");
            }
    }

    private void onServerStop(ServerStoppingEvent event) {
        executor.shutdownNow();
    }

    public static void sendMessageToOppedPlayers(String message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendMessage(new TextComponent(message), player.getUUID());

            }
        }
    }
}