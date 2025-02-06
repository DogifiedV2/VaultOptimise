package com.dog.vaultoptimise.mixin;


import com.dog.vaultoptimise.commands.MainCommand;
import com.dog.vaultoptimise.config.ServerConfig;
import iskallia.vault.block.VaultPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = VaultPortalBlock.class)
public class MixinVaultPortalBlock {

    private static final Map<UUID, Long> lastVaultLockedMsgTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_INTERVAL_MS = 5000;

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER && level.dimension() != Level.END) {
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        String playerName = player.getName().getString();
        Component waitMessage;

        if (MainCommand.vaultsLocked) {
            long now = System.currentTimeMillis();
            UUID playerUUID = player.getUUID();
            long lastSent = lastVaultLockedMsgTime.getOrDefault(playerUUID, 0L);

            if (now - lastSent >= MESSAGE_INTERVAL_MS) {
                lastVaultLockedMsgTime.put(playerUUID, now);
                com.dog.vaultoptimise.VaultOptimise.LOGGER.warn("Denying vault entry for {} - vaults are locked!", playerName);
                waitMessage = Component.nullToEmpty("Vaults are currently locked, you may not enter at this time.")
                        .copy().withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA)));
                serverPlayer.sendMessage(waitMessage, ChatType.GAME_INFO, playerUUID);
                serverPlayer.sendMessage(waitMessage, ChatType.CHAT, playerUUID);
            }

            player.setPortalCooldown();
            ci.cancel();
            return;
        }
    }
}