package com.dog.vaultoptimise.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Properties;

/** Startup-only switches: Forge server configs load after mixin selection. */
public final class AutomationConfig {
    public static final String FILE_NAME = "vaultoptimise-automation.properties";

    public enum Patch {
        COAL_TO_STEEL("coalToSteelGuard"),
        STEEL_TO_IRON("steelToIronGuard"),
        POWAH_REDSTONE("powahIgnoredRedstone"),
        BOTANY_RECIPES("botanyRecipeLookup"),
        VAULT_FILTERS("vaultFiltersCache"),
        DIFFUSER("diffuserUnusedScan");

        public final String key;

        Patch(String key) {
            this.key = key;
        }
    }

    private final EnumSet<Patch> enabled;

    private AutomationConfig(EnumSet<Patch> enabled) {
        this.enabled = enabled;
    }

    public boolean enabled(Patch patch) {
        return this.enabled.contains(patch);
    }

    public static AutomationConfig load(Path file) throws IOException {
        Properties values = new Properties();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                values.load(reader);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid automation config: " + file, e);
            }
        }

        boolean changed = false;
        EnumSet<Patch> enabled = EnumSet.noneOf(Patch.class);
        for (Patch patch : Patch.values()) {
            String value = values.getProperty(patch.key);
            if (value == null) {
                values.setProperty(patch.key, "true");
                value = "true";
                changed = true;
            }
            value = value.trim();
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IOException("Expected true or false for " + patch.key + " in " + file);
            }
            if (Boolean.parseBoolean(value)) {
                enabled.add(patch);
            }
        }

        if (changed) {
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, FILE_NAME, ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(temporary)) {
                    values.store(writer, "VaultOptimise automation patches. Restart required.\n"
                            + "All patches default to true. Set a patch to false to disable it.\n"
                            + "Missing or unsupported mod versions always skip their patches.\n"
                            + "Existing JVM disable flags still apply; they cannot override false here.");
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        return new AutomationConfig(enabled);
    }
}
