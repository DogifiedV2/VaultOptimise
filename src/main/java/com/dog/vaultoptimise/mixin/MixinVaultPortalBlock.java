package com.dog.vaultoptimise.mixin;


import com.dog.vaultoptimise.config.ServerConfig;
import com.dog.vaultoptimise.saving.AutoSaveHandler;
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

@Mixin(value = VaultPortalBlock.class)
public class MixinVaultPortalBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player) {
            if (level.dimension() == Level.OVERWORLD) {
                if (AutoSaveHandler.currentlySaving && ServerConfig.CONFIG_VALUES.SafeSaving.get()) {
                    ServerPlayer serverPlayer = (ServerPlayer) player;

                    if (AutoSaveHandler.addPlayerToQueue(serverPlayer)) {
                        com.dog.vaultoptimise.VaultOptimise.LOGGER.warn("Delaying vault entry for player {} due to server save.", player.getName().getString());
                        Component waitMessage = Component.nullToEmpty("Server is currently saving, please try again in a few seconds.")
                                .copy().withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA)));
                        serverPlayer.sendMessage(waitMessage, ChatType.GAME_INFO, player.getUUID());
                        serverPlayer.sendMessage(waitMessage, ChatType.CHAT, player.getUUID());
                        player.setPortalCooldown();
                    }
                    ci.cancel();
                }
            }
        }
    }
}