package com.dog.vaultoptimise.mixin;


import com.dog.vaultoptimise.commands.MainCommand;
import com.dog.vaultoptimise.events.VaultLockHandler;
import iskallia.vault.block.VaultPortalBlock;
import net.minecraft.core.BlockPos;
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
        if (!MainCommand.vaultsLocked || level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER && level.dimension() != Level.END) {
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (VaultLockHandler.sendLockedMessage(serverPlayer, true)) {
            com.dog.vaultoptimise.VaultOptimise.LOGGER.warn(
                    "Denying vault entry for {} - vaults are locked by admins!",
                    player.getName().getString()
            );
        }

        player.setPortalCooldown();
        ci.cancel();
    }
}
