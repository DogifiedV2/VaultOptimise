package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.VaultOptimise;
import com.dog.vaultoptimise.config.ServerConfig;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.core.jmx.Server;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CrashEvents {

    private static final Path CRASH_REPORT_DIR = Paths.get("crash-reports");
    private static final String WEBHOOK_URL = ServerConfig.CONFIG_VALUES.webhookURL.get();
    private static final String WEBHOOK_REGEX = "^https:\\/\\/discord\\.com\\/api\\/webhooks\\/\\d+\\/[-_A-Za-z0-9]+$";


    public static boolean isValidWebhookURL(String url) {
        Pattern pattern = Pattern.compile(WEBHOOK_REGEX);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppedEvent event) {
        (new Thread(() -> {
            boolean isCrash = event.getServer().isRunning();
            if (isCrash) {
                if (ServerConfig.CONFIG_VALUES.pingOnCrash.get()) {
                    sendDiscordMessage("@everyone", WEBHOOK_URL);
                }
                sendDiscordMessage("Server crash detected.", WEBHOOK_URL);
                File crashReport = getLatestCrashReport();
                if (crashReport.exists()) {
                    sendDiscordFile("Crash report attached.", crashReport, WEBHOOK_URL);
                } else {
                    sendDiscordMessage("Crash report file not found", WEBHOOK_URL);
                }
            }

        })).start();
    }

    public static File getLatestCrashReport() {
        File dir = CRASH_REPORT_DIR.toFile();
        return dir.exists() && dir.isDirectory() ? (File) Arrays.stream(dir.listFiles((d, name) -> name.endsWith(".txt"))).max(Comparator.comparingLong(File::lastModified)).orElse((File) null) : null;
    }


    public static void sendDiscordMessage(String message, String webhookUrl) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            String jsonPayload = "{\"content\":\"" + message + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes());
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                VaultOptimise.LOGGER.error("Failed to send message to Discord. Response code: {}", responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void sendDiscordFile(String message, File file, String webhookUrl) {
        try {
            String boundary = "------------------------" + System.currentTimeMillis();
            HttpURLConnection connection = (HttpURLConnection) (new URL(webhookUrl)).openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            StringBuilder payload = new StringBuilder();
            payload.append("--").append(boundary).append("\r\n");
            payload.append("Content-Disposition: form-data; name=\"content\"\r\n\r\n");
            payload.append(message).append("\r\n");
            payload.append("--").append(boundary).append("\r\n");
            payload.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            payload.append("Content-Type: text/plain\r\n\r\n");
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(payload.toString().getBytes());
            Files.copy(file.toPath(), outputStream);
            outputStream.write(("\r\n--" + boundary + "--\r\n").getBytes());
            outputStream.flush();
            outputStream.close();
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                VaultOptimise.LOGGER.info("Crash report sent successfully.");
            } else {
                VaultOptimise.LOGGER.error("Failed to send crash report. Response code: {}", responseCode);
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    String errorResponse = new String(errorStream.readAllBytes());
                    VaultOptimise.LOGGER.error("Discord error response: {}", errorResponse);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
