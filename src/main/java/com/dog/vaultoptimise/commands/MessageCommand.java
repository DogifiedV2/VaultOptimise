package com.dog.vaultoptimise.commands;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.util.vaultPlayer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber
public class MessageCommand {

    private static final HashMap<UUID, UUID> lastMessageSender = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST) // Ensure our command is the last one registered
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();

        root.getChildren().removeIf(node ->
                node.getName().equals("msg") ||
                        node.getName().equals("tell") ||
                        node.getName().equals("w")
        );

        registerCustomMessageCommands(dispatcher);
    }

    private static void registerCustomMessageCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        List<String> messageCommands = Arrays.asList("msg", "tell", "w");

        for (String cmd : messageCommands) {
            dispatcher.register(
                    Commands.literal(cmd)
                            .executes(context -> {
                                context.getSource().sendFailure(new TextComponent("Usage: /" + cmd + " <player> <message>"));
                                return 0;
                            })
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                            getOnlinePlayerNames(context), builder))
                                    .executes(context -> {
                                        context.getSource().sendFailure(new TextComponent("Please specify a message"));
                                        return 0;
                                    })
                                    .then(Commands.argument("message", StringArgumentType.greedyString())
                                            .executes(MessageCommand::sendMessage)))
            );
        }

        dispatcher.register(
                Commands.literal("m")
                        .executes(context -> {
                            context.getSource().sendFailure(new TextComponent("Usage: /m <player> <message>"));
                            return 0;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        getOnlinePlayerNames(context), builder))
                                .executes(context -> {
                                    context.getSource().sendFailure(new TextComponent("Please specify a message"));
                                    return 0;
                                })
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(MessageCommand::sendMessage)))
        );

        List<String> replyCommands = Arrays.asList("r", "reply");
        for (String cmd : replyCommands) {
            dispatcher.register(
                    Commands.literal(cmd)
                            .executes(context -> {
                                context.getSource().sendFailure(new TextComponent("Usage: /" + cmd + " <message>"));
                                return 0;
                            })
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(MessageCommand::replyMessage))
            );
        }
    }

    public static List<String> getOnlinePlayerNames(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID senderUuid = null;

        try {
            ServerPlayer sender = context.getSource().getPlayerOrException();
            senderUuid = sender.getUUID();
        } catch (CommandSyntaxException ignored) {
        }

        final UUID finalSenderUuid = senderUuid;

        return server.getPlayerList().getPlayers().stream()
                .filter(player -> finalSenderUuid == null || !player.getUUID().equals(finalSenderUuid))
                .map(player -> player.getName().getString())
                .toList();
    }

    private static int sendMessage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer sender = source.getPlayerOrException();
        String targetName = StringArgumentType.getString(context, "player");
        String message = StringArgumentType.getString(context, "message");

        ServerPlayer targetPlayer = vaultPlayer.getPlayerByUsername(targetName);

        if (targetPlayer == null) {
            source.sendFailure(new TextComponent("Player not found: " + targetName).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (targetPlayer.getUUID().equals(sender.getUUID())) {
            source.sendFailure(new TextComponent("You cannot message yourself").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean success = deliverMessage(sender, targetPlayer, message);

        if (success) {
            lastMessageSender.put(targetPlayer.getUUID(), sender.getUUID());
            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }

    private static int replyMessage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer sender = source.getPlayerOrException();
        String message = StringArgumentType.getString(context, "message");

        if (!lastMessageSender.containsKey(sender.getUUID())) {
            source.sendFailure(new TextComponent("You have nobody to reply to").withStyle(ChatFormatting.RED));
            return 0;
        }

        UUID lastSenderUUID = lastMessageSender.get(sender.getUUID());
        ServerPlayer lastSender = vaultPlayer.getPlayerByUUID(lastSenderUUID);

        if (lastSender == null) {
            source.sendFailure(new TextComponent("That player is no longer online").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean success = deliverMessage(sender, lastSender, message);

        if (success) {
            lastMessageSender.put(lastSender.getUUID(), sender.getUUID());
            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }

    private static boolean deliverMessage(ServerPlayer sender, ServerPlayer recipient, String message) {
        try {
            MutableComponent senderMessage = new TextComponent("You ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent("-> ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(new TextComponent(recipient.getName().getString() + ": ")
                            .withStyle(ChatFormatting.GOLD))
                    .append(new TextComponent(message)
                            .withStyle(ChatFormatting.WHITE));

            MutableComponent recipientMessage = new TextComponent(sender.getName().getString() + " ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(new TextComponent("-> ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(new TextComponent("You: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(new TextComponent(message)
                            .withStyle(ChatFormatting.WHITE));

            sender.sendMessage(senderMessage, sender.getUUID());
            recipient.sendMessage(recipientMessage, recipient.getUUID());

            return true;
        } catch (Exception e) {
            VaultOptimise.LOGGER.error("Error delivering message", e);
            return false;
        }
    }

    public static void handlePlayerLogout(UUID playerUUID) {
        lastMessageSender.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handlePlayerLogout(player.getUUID());
        }
    }
}