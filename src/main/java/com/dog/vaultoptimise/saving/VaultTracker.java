package com.dog.vaultoptimise.saving;

import com.mojang.logging.LogUtils;
import iskallia.vault.init.ModBlocks;
import iskallia.vault.init.ModItems;
import net.minecraft.network.chat.ChatType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.*;

@Mod.EventBusSubscriber
public class VaultTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Long> completedVaults = new ArrayList<>();

    public static void addCompletedVault() {
        completedVaults.add(getCurrentTimestamp());
    }

    public static void removeCompletedVault(String playerName) {
        completedVaults.remove(playerName);
    }

    public static boolean hasCompletedVaults() {
        long currentTime = getCurrentTimestamp();

        completedVaults.removeIf(timestamp -> currentTime - timestamp > 30);
        return !completedVaults.isEmpty();
    }

    public static void cleanCompletedVaults() {
        completedVaults.clear();
    }

    private static long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

}
