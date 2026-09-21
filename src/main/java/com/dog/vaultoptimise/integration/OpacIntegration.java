package com.dog.vaultoptimise.integration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.UUID;

/**
 * Soft-dependency facade for Open Parties and Claims (OPaC).
 *
 * All public methods are safe to call regardless of whether OPaC is installed at
 * runtime. The actual OPaC type references live in OpacIntegrationImpl, which the
 * JVM only links when OPaC is present — preventing NoClassDefFoundError on servers
 * that don't run OPaC.
 */
public final class OpacIntegration {

    private static final String OPAC_MOD_ID = "openpartiesandclaims";

    private static final boolean OPAC_LOADED = ModList.get().isLoaded(OPAC_MOD_ID);

    private OpacIntegration() {}

    /**
     * Returns true if OPaC is installed and the chunk at the given position in the
     * given level is currently claimed by any player. Returns false if OPaC is not
     * installed, the chunk is unclaimed, or the OPaC API call fails for any reason.
     */
    public static boolean isClaimed(ServerLevel level, ChunkPos pos) {
        if (!OPAC_LOADED) return false;
        try {
            return OpacIntegrationImpl.isClaimed(level, pos);
        } catch (Exception opacFailure) {
            return false;
        } catch (LinkageError opacLinkageFailure) {
            return false;
        }
    }

    /**
     * Returns true when OPaC is installed in the current Forge environment.
     * Useful for status reporting and command output.
     */
    public static boolean isLoaded() {
        return OPAC_LOADED;
    }

    /**
     * Returns the UUID of the player who owns the claim covering the given chunk,
     * or empty if the chunk is unclaimed, OPaC is not loaded, or the lookup fails.
     */
    public static Optional<UUID> getClaimOwnerId(ServerLevel level, ChunkPos pos) {
        if (!OPAC_LOADED) return Optional.empty();
        try {
            return OpacIntegrationImpl.getClaimOwnerId(level, pos);
        } catch (Exception opacFailure) {
            return Optional.empty();
        } catch (LinkageError opacLinkageFailure) {
            return Optional.empty();
        }
    }
}
