package com.dog.vaultoptimise.events;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

public class DimensionChangeEvent {

    @SubscribeEvent
    public static void onDimChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        String previousDimensionNamespace = event.getFrom().location().getNamespace();

        if ("the_vault".equals(previousDimensionNamespace) && event.getTo().equals(Level.OVERWORLD)) {
            event.getPlayer().removeEffect(MobEffects.BAD_OMEN);
            event.getPlayer().removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        }

        }

    @SubscribeEvent
    public static void onEnterVault(PlayerEvent.PlayerChangedDimensionEvent event) {
        String previousDimensionNamespace = event.getFrom().location().getNamespace();
        String newDimensionNamespace = event.getTo().location().getNamespace();
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        ServerLevel playerLevel = player.getLevel();

        if (!previousDimensionNamespace.equals("minecraft") && newDimensionNamespace.equals("the_vault")) {

            Optional<Vault> vault = ServerVaults.get(playerLevel);
            if (vault.isPresent()) {

            }
        }
    }



}
