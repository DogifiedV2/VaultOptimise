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

        public final ForgeConfigSpec.ConfigValue<Integer> chunkSaveDelay;
        public final ForgeConfigSpec.ConfigValue<Integer> chunksPerTick;
        public final ForgeConfigSpec.ConfigValue<Boolean> debugLogging;
        public final ForgeConfigSpec.ConfigValue<Boolean> pteroKill;

        public final ForgeConfigSpec.BooleanValue MobAIControl;
        public final ForgeConfigSpec.BooleanValue VaultRaidEffect;
        public final ForgeConfigSpec.DoubleValue ActivationRadius;
        public final ForgeConfigSpec.DoubleValue ActivationHeight;
        public final ForgeConfigSpec.DoubleValue VaultActivationRadius;
        public final ForgeConfigSpec.ConfigValue<List<String>> ExemptUsernames;
        public final ForgeConfigSpec.ConfigValue<String> webhookURL;
        public final ForgeConfigSpec.ConfigValue<Boolean> pingOnCrash;

        Config(ForgeConfigSpec.Builder builder) {
            builder.push("Smooth Saving");

            builder.comment(" This feature staggers chunk saves over 5 minutes, saving 10+ per tick, ensuring smoother performance. This can not be disabled without uninstalling the mod.");

            builder.comment("Delay before your chunk is saved to disk. Defaults to 5 minutes // 300 seconds");
            this.chunkSaveDelay = builder.defineInRange("chunkSaveDelay", 300, 100, 3600);

            builder.comment("Amount of chunks to be saved per tick. You can increment this, but 10 is recommended; 5 minutes have 6000 ticks, which can save 60,000 chunks.");
            this.chunksPerTick = builder.defineInRange("chunksPerTick", 10, 5, 100);

            builder.comment("Enables debug logging. Intended for testing purposes, this will spam your console. Hard.");
            this.debugLogging = builder.define("debugLogging", false);


            builder.pop();
            builder.push("AI Control");
            MobAIControl = builder.comment(" Mob AI will be controlled to reduce entity lag.").define("MobAIControl", true);
            ActivationRadius = builder.comment("Distance that a player has to be before the AI is turned on (X and Z)")
                    .defineInRange("ActivationRadius", 48.0, 5.0, 500.0);

            ActivationHeight = builder.comment("Activation Radius but for the Y coordinate. Useful for caves.")
                    .defineInRange("ActivationHeight", 10.0, 5.0, 500.0);

            VaultActivationRadius = builder.comment("Activation radius for mobs in the vault.")
                    .defineInRange("VaultActivationRadius", 96.0, 48.0, 500.0);

            builder.pop();
            builder.push("Crash Detection");

            webhookURL = builder.comment(" Paste a Discord Webhook URL in order to enable crash detection. Logs will be sent directly to discord.")
                    .define("webhookURL", "");

            pingOnCrash = builder.comment(" Whether to ping @everyone upon a crash (make your channel private..)")
                    .define("PingOnCrash", false);


            builder.pop();
            builder.push("Other");
            VaultRaidEffect = builder.comment(" Remove raid effects from users upon leaving the vault").define("VaultRaidEffect", false);
            ExemptUsernames = builder.comment(" List of users that will not be kicked during lockdown.")
                    .define("ExemptUsernames", new ArrayList<>(List.of("Admin", "YourUsernameHere")));

            pteroKill = builder.comment(" DEDICATED Pterodactyl only! Vault Hunter pterodactyl servers dont tend to fully shut down after the minecraft server does. This will make sure it does.").define("pteroKill", false);


            builder.pop();
        }
    }

}
