package com.dog.vaultoptimise.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {

    public static final ForgeConfigSpec CONFIG;
    public static final Config CONFIG_VALUES;

    static {
        final Pair<Config, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Config::new);
        CONFIG = specPair.getRight();
        CONFIG_VALUES = specPair.getLeft();
    }

    public static class Config {
        public final ForgeConfigSpec.BooleanValue AutoSaveLogs;
        public final ForgeConfigSpec.BooleanValue AutoSaves;
        public final ForgeConfigSpec.BooleanValue MobAIControl;
        public final ForgeConfigSpec.BooleanValue VaultRaidEffect;
        public final ForgeConfigSpec.BooleanValue SafeSaving;
        public final ForgeConfigSpec.BooleanValue LightUpdates;
        public final ForgeConfigSpec.DoubleValue ActivationRadius;
        public final ForgeConfigSpec.DoubleValue ActivationHeight;
        public final ForgeConfigSpec.ConfigValue<List<String>> ExemptUsernames;
        public final ForgeConfigSpec.ConfigValue<List<String>> Dimensions;

        Config(ForgeConfigSpec.Builder builder) {
            builder.push("Saves");

            AutoSaves = builder.comment(" Asynchronously save the world every 5 minutes").define("AutoSaves", true);
            SafeSaving = builder.comment(" Prevents players from entering portals while saves occur").define("SafeSaving", true);
            AutoSaveLogs = builder.comment(" Enable in-game logs for auto saves (OP only)").define("AutoSaveLogs", false);
            LightUpdates = builder.comment(" (Experimental) Light updates are a rare cause for async crashes. Enabling this will pause them during saves.").define("LightUpdates", false);
            Dimensions = builder.comment(" List of dimensions the mod will save. To get a correct name, check your console logs, and copy the value after Skipping Dimension.\n Vault Dimensions are blacklisted.\n Example: [\"minecraft:overworld\", \"ae2:spatial_storage\", \"minecraft:the_nether\", \"minecraft:the_end\"]")
                    .define("DimensionsToSave", new ArrayList<>(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")));

            builder.pop();
            builder.push("AI Control");
            MobAIControl = builder.comment(" Mob AI will be controlled to reduce entity lag").define("MobAIControl", true);
            ActivationRadius = builder.comment("Distance that a player has to be before the AI is turned on (X and Z)")
                    .defineInRange("ActivationRadius", 48.0, 5.0, 500.0);

            ActivationHeight = builder.comment("Activation Radius but for the Y coordinate. Useful for caves.")
                    .defineInRange("ActivationHeight", 10.0, 5.0, 500.0);

            builder.pop();
            builder.push("Other");
            VaultRaidEffect = builder.comment(" Remove raid effects from users upon leaving the vault").define("VaultRaidEffect", false);
            ExemptUsernames = builder.comment(" List of users that will not be kicked during lockdown.")
                    .define("ExemptUsernames", new ArrayList<>(List.of("Admin", "YourUsernameHere")));


            builder.pop();
        }
    }

}
