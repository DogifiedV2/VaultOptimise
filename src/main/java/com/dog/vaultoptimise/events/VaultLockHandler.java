package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.commands.MainCommand;
import iskallia.vault.item.crystal.VaultCrystalItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VaultLockHandler {

    private static final String LOCKED_MESSAGE = "Vaults are currently locked by admins";
    private static final long MESSAGE_INTERVAL_MS = 5000;
    private static final Map<UUID, Long> lastVaultLockedMsgTime = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onCrystalUse(PlayerInteractEvent.RightClickBlock event) {
        if (!MainCommand.vaultsLocked || event.getWorld().isClientSide()) {
            return;
        }

        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!(event.getItemStack().getItem() instanceof VaultCrystalItem)) {
            return;
        }

        sendLockedMessage(serverPlayer, false);
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    public static boolean sendLockedMessage(ServerPlayer player, boolean throttle) {
        UUID playerUUID = player.getUUID();
        long now = System.currentTimeMillis();

        if (throttle) {
            long lastSent = lastVaultLockedMsgTime.getOrDefault(playerUUID, 0L);
            if (now - lastSent < MESSAGE_INTERVAL_MS) {
                return false;
            }
        }

        lastVaultLockedMsgTime.put(playerUUID, now);
        Component message = new TextComponent(LOCKED_MESSAGE).withStyle(ChatFormatting.GRAY);
        player.sendMessage(message, ChatType.GAME_INFO, playerUUID);
        player.sendMessage(message, ChatType.CHAT, playerUUID);
        return true;
    }
}
