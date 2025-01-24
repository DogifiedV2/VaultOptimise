package com.dog.vaultoptimise.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

public class vaultPlayer {

    public static ServerPlayer getPlayerByUUID(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server.getPlayerList().getPlayer(uuid);
    }

    public static UUID getUUIDFromName(String playerName, MinecraftServer server) {
        GameProfileCache profileCache = server.getProfileCache();
        GameProfile profile = profileCache.get(playerName).orElse(null);

        if (profile == null) {
            throw new IllegalArgumentException("Player with name '" + playerName + "' not found!");
        }

        return profile.getId();
    }

    public static String getNameFromUUID(String UUID, MinecraftServer server) {
        GameProfileCache profileCache = server.getProfileCache();
        GameProfile profile = profileCache.get(UUID).orElse(null);

        if (profile == null) {
            throw new IllegalArgumentException("Player with name '" + UUID + "' not found!");
        }

        return profile.getName();
    }

}
