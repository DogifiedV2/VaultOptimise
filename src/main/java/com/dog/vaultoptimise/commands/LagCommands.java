package com.dog.vaultoptimise.commands;

import com.dog.vaultoptimise.config.ServerConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Mod.EventBusSubscriber
public class LagCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("vaultoptimise")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("lag")
                        .then(Commands.literal("iteminfo")
                                .executes(LagCommands::checkItems))
                        .then(Commands.literal("mobinfo")
                                .executes(LagCommands::returnMobInfo)))
        );
    }


    private static int returnMobInfo(CommandContext<CommandSourceStack> context) {
        if (ServerConfig.CONFIG_VALUES.MobAIControl.get()) {
            checkMobAiStatus(context);
        } else {
            context.getSource().sendSuccess(new net.minecraft.network.chat.TextComponent("This feature is not enabled in your config."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void checkMobAiStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int vaultAiOnCount = 0;
        int vaultAiOffCount = 0;
        int otherAiOnCount = 0;
        int otherAiOffCount = 0;
        int mobSpawnersCount = 0;
        Map<String, Integer> mobs = new HashMap<>();

        for (ServerLevel level : source.getServer().getAllLevels()) {
            // Get the dimension's path and check if it contains "vault" (case-insensitive)
            String dimPath = level.dimension().location().getPath().toLowerCase();
            boolean isVault = dimPath.contains("vault");

            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PathfinderMob mob) {
                    CompoundTag nbt = mob.getPersistentData();
                    if (nbt.contains("CustomSpawnReason")) {
                        mobs.merge(nbt.getString("CustomSpawnReason"), 1, Integer::sum);
                        if (mob.isNoAi()) {
                            if (isVault) {
                                vaultAiOffCount++;
                            } else {
                                otherAiOffCount++;
                            }
                        } else {
                            if (nbt.contains("spawner")) {
                                mobSpawnersCount++;
                            } else {
                                if (isVault) {
                                    vaultAiOnCount++;
                                } else {
                                    otherAiOnCount++;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create nicely formatted components with colors
        Component vaultHeader = Component.nullToEmpty("Vault Dimensions:").copy().withStyle(ChatFormatting.GOLD);
        Component vaultAiOnText = Component.nullToEmpty("  AI on: " + vaultAiOnCount).copy().withStyle(ChatFormatting.GREEN);
        Component vaultAiOffText = Component.nullToEmpty("  AI off: " + vaultAiOffCount).copy().withStyle(ChatFormatting.RED);
        Component otherHeader = Component.nullToEmpty("Other Dimensions:").copy().withStyle(ChatFormatting.GOLD);
        Component otherAiOnText = Component.nullToEmpty("  AI on: " + otherAiOnCount).copy().withStyle(ChatFormatting.GREEN);
        Component otherAiOffText = Component.nullToEmpty("  AI off: " + otherAiOffCount).copy().withStyle(ChatFormatting.RED);
        Component spawnerText = Component.nullToEmpty("iSpawner mobs: " + mobSpawnersCount).copy().withStyle(ChatFormatting.RED);

        // Send the messages to the command source
        source.sendSuccess(vaultHeader, false);
        source.sendSuccess(vaultAiOnText, false);
        source.sendSuccess(vaultAiOffText, false);
        source.sendSuccess(otherHeader, false);
        source.sendSuccess(otherAiOnText, false);
        source.sendSuccess(otherAiOffText, false);
        source.sendSuccess(spawnerText, false);

        System.out.println(mobs);
    }


    private static int checkItems(CommandContext<CommandSourceStack> context) {
        ServerLevel world = ServerLifecycleHooks.getCurrentServer().getLevel(Level.OVERWORLD);
        if (world == null) {
            return Command.SINGLE_SUCCESS;
        }

        if (context.getSource().getEntity() == null) {
            return 0;
        }

        Player player = (Player) context.getSource().getEntity();

        // Convert entities to a list of item entities
        List<ItemEntity> itemEntities = StreamSupport.stream(world.getEntities().getAll().spliterator(), false)
                .filter(entity -> entity instanceof ItemEntity)
                .map(entity -> (ItemEntity) entity)
                .toList();

        int totalItems = 0;
        int totalStacks = 0;

        // Map to track item counts per chunk
        Map<ChunkPos, Integer> chunkItemCount = new HashMap<>();

        for (ItemEntity itemEntity : itemEntities) {
            int itemCount = itemEntity.getItem().getCount(); // Number of items in this stack
            totalItems += itemCount;
            totalStacks++;

            ChunkPos chunkPos = new ChunkPos(itemEntity.blockPosition());
            chunkItemCount.put(chunkPos, chunkItemCount.getOrDefault(chunkPos, 0) + itemCount);
        }

        // Get the top 3 chunks with the most items
        List<Map.Entry<ChunkPos, Integer>> topChunks = chunkItemCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(3)
                .toList();

        player.sendMessage(new TextComponent("Total individual items: " + totalItems), player.getUUID());
        player.sendMessage(new TextComponent("Total item stacks: " + totalStacks), player.getUUID());

        if (!topChunks.isEmpty()) {
            for (int i = 0; i < topChunks.size(); i++) {
                Map.Entry<ChunkPos, Integer> entry = topChunks.get(i);
                ChunkPos chunkPos = entry.getKey();
                int itemCount = entry.getValue();

                TextComponent message = new TextComponent("#" + (i + 1) + " location: " + itemCount + " items at ["
                        + chunkPos.getMinBlockX() + ", " + chunkPos.getMinBlockZ() + "]");

                message.setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/tp " + player.getName().getString() + " " + chunkPos.getMinBlockX() + " ~ " + chunkPos.getMinBlockZ()))
                        .withColor(ChatFormatting.RED)
                        .withBold(false)
                );

                player.sendMessage(message, player.getUUID());
            }
        } else {
            player.sendMessage(new TextComponent("No items found in any chunks."), player.getUUID());
        }
        return Command.SINGLE_SUCCESS;
    }

}
