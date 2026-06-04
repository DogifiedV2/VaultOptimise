package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.VaultOptimise;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VaultOptimise.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemTossTracker {

    private static final Path LOGS_DIR = Paths.get("logs", "item-tosses");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final long BURST_WINDOW_MILLIS = 30_000L;
    private static final long BURST_LOG_COOLDOWN_MILLIS = 5_000L;
    private static final int BURST_STACK_THRESHOLD = 50;
    private static final int BURST_ITEM_THRESHOLD = 1_000;

    private static final Map<UUID, ArrayDeque<TossRecord>> RECENT_TOSSES = new HashMap<>();
    private static final Map<UUID, Long> LAST_BURST_LOG = new HashMap<>();

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer) return;

        ItemEntity entity = event.getEntityItem();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;

        long now = System.currentTimeMillis();
        logToss(player, entity, stack);
        trackBurst(player, entity, stack, now);
    }

    private static void logToss(ServerPlayer player, ItemEntity entity, ItemStack stack) {
        String itemName = getRegistryString(stack);
        String nbtData = stack.hasTag() ? stack.getTag().toString() : "";
        String dimension = player.getLevel().dimension().location().toString();

        String message = String.format(
                "DROP player=%s uuid=%s entity=%s dim=%s pos=%.2f,%.2f,%.2f count=%d item=%s%s",
                player.getName().getString(),
                player.getUUID(),
                entity.getUUID(),
                dimension,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                stack.getCount(),
                itemName,
                nbtData.isEmpty() ? "" : " nbt=" + nbtData
        );

        writeLog(message);
    }

    private static void trackBurst(ServerPlayer player, ItemEntity entity, ItemStack stack, long now) {
        UUID playerId = player.getUUID();
        ArrayDeque<TossRecord> records = RECENT_TOSSES.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        records.addLast(new TossRecord(now, stack.getCount()));

        while (!records.isEmpty() && now - records.peekFirst().timestampMillis > BURST_WINDOW_MILLIS) {
            records.removeFirst();
        }

        int stackEvents = records.size();
        int itemCount = records.stream().mapToInt(record -> record.itemCount).sum();
        if (stackEvents < BURST_STACK_THRESHOLD && itemCount < BURST_ITEM_THRESHOLD) return;

        long lastLog = LAST_BURST_LOG.getOrDefault(playerId, 0L);
        if (now - lastLog < BURST_LOG_COOLDOWN_MILLIS) return;

        LAST_BURST_LOG.put(playerId, now);
        writeLog(String.format(
                "BURST player=%s uuid=%s window=%ds stacks=%d items=%d last_entity=%s dim=%s pos=%.2f,%.2f,%.2f last_count=%d last_item=%s",
                player.getName().getString(),
                playerId,
                BURST_WINDOW_MILLIS / 1000L,
                stackEvents,
                itemCount,
                entity.getUUID(),
                player.getLevel().dimension().location(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                stack.getCount(),
                getRegistryString(stack)
        ));
    }

    private static void writeLog(String message) {
        String timestamp = "[" + LocalDateTime.now().format(TIME_FORMAT) + "]";
        String logMessage = timestamp + " " + message;

        try {
            try {
                if (Files.notExists(LOGS_DIR)) {
                    Files.createDirectories(LOGS_DIR);
                }
            } catch (IOException e) {
                VaultOptimise.LOGGER.warn("Failed to create item toss logs directory: {}", e.getMessage());
                LOGS_DIR.getParent().toFile().mkdirs();
            }

            Path logFile = LOGS_DIR.resolve(LocalDateTime.now().format(FILE_FORMAT) + ".log");
            try {
                Files.writeString(logFile, logMessage + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (AccessDeniedException e) {
                Path fallbackLog = Paths.get("item_tosses.log");
                Files.writeString(fallbackLog, logMessage + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                VaultOptimise.LOGGER.warn("Item toss action written to fallback log: {}", logMessage);
            }
        } catch (Exception e) {
            VaultOptimise.LOGGER.warn("Error logging item toss: {}", e.getMessage());
            VaultOptimise.LOGGER.warn("ITEM TOSS: {}", logMessage);
        }
    }

    private static String getRegistryString(ItemStack stack) {
        ResourceLocation registryName = stack.getItem().getRegistryName();
        return registryName == null ? "NULL" : registryName.toString();
    }

    private record TossRecord(long timestampMillis, int itemCount) {
    }
}
