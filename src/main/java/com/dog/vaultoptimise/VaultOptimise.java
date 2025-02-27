package com.dog.vaultoptimise;

import com.dog.vaultoptimise.events.*;
import com.dog.vaultoptimise.config.ServerConfig;
import com.mojang.logging.LogUtils;
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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Random;

@Mod("vaultoptimise")
public class VaultOptimise {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "vaultoptimise";
    public static Random rand = new Random();

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

        if (CrashEvents.isValidWebhookURL(ServerConfig.CONFIG_VALUES.webhookURL.get())) {
            MinecraftForge.EVENT_BUS.register(CrashEvents.class);
            logInfo("Crash Detection started");
        }

        if (ServerConfig.CONFIG_VALUES.MobAIControl.get()) {
            MinecraftForge.EVENT_BUS.register(AIControl.class);
            logInfo("Mob AI Control started");
        }

        if (ServerConfig.CONFIG_VALUES.extremeMode.get()) {
            MobSpawningHandler.startSpawningSystem();
            logInfo("Extreme Mob Control started");
        }

        if (ServerConfig.CONFIG_VALUES.VaultRaidEffect.get()) {
            MinecraftForge.EVENT_BUS.register(DimensionChangeEvent.class);
            logInfo("Vault Raid fix started");
        }

        if (ServerConfig.CONFIG_VALUES.pteroKill.get()) {
            VaultOptimise.LOGGER.info("Listening for server shutdown.");
            LogListener.register();
        }
    }

    public static void forceKillProcess() {
        try {
            RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
            String pid = runtimeMxBean.getName().split("@")[0];
            VaultOptimise.LOGGER.warn("Forcing process termination. PID: " + pid);
            //Runtime.getRuntime().exec("kill -9 " + pid);
            System.exit(0);
        } catch (Exception e) {
            VaultOptimise.LOGGER.error("Failed to kill process: " + e.getMessage(), e);
        }
    }


    private void onServerStop(ServerStoppingEvent event) {

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