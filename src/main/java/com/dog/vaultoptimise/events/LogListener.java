package com.dog.vaultoptimise.events;

import com.dog.vaultoptimise.VaultOptimise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.LogEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


public class LogListener extends AbstractAppender {

    private boolean isShuttingDown = false;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }
    });

    public LogListener() {
        super("VaultOptimiseLogListener", null, null);
    }

    @Override
    public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();

        if (event.getLoggerName().contains("net.minecraft.server.MinecraftServer") && message.contains("Stopping server")) {
            VaultOptimise.LOGGER.warn("Stopping server detected");
            isShuttingDown = true;
        }

        if (isShuttingDown && message.contains("All dimensions are saved")) {
            VaultOptimise.LOGGER.warn("Instance will shut down in 5 seconds.");
            scheduler.schedule(() -> {
                VaultOptimise.LOGGER.warn("Stopping instance.");
                VaultOptimise.forceKillProcess();
            }, 5, TimeUnit.SECONDS);
        }
    }

    public static void register() {
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        LogListener listener = new LogListener();
        rootLogger.addAppender(listener);
        listener.start();
    }
}