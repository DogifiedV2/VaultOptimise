package com.dog.vaultoptimise.events;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import top.theillusivec4.curios.api.CuriosApi;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BotaniaCuriosHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        Player player = event.player;
        String dimension = player.level.dimension().location().toString();

        if (dimension.contains("vault")) {
            CuriosApi.getCuriosHelper().getEquippedCurios(player).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    Item item = handler.getStackInSlot(i).getItem();
                    ResourceLocation itemId = item.getRegistryName();
                    if (itemId != null && itemId.getNamespace().equals("botania")) {
                        if (!itemId.toString().contains("cosmetic")) {
                            handler.setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }
                }
            });
        }
    }
}