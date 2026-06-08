package com.dog.vaultoptimise.integration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the actual OPaC type references. This class is only loaded by the JVM
 * after OpacIntegration confirms OPaC is installed; on servers without OPaC the
 * class never resolves, so its imports never trigger a linkage error.
 */
final class OpacIntegrationImpl {

    private OpacIntegrationImpl() {}

    /**
     * Direct OPaC API call. Returns true when any player claim covers the given
     * chunk in the given dimension. The OPaC API returns null for unclaimed chunks.
     */
    static boolean isClaimed(ServerLevel level, ChunkPos pos) {
        return lookupClaim(level, pos) != null;
    }

    /**
     * Direct OPaC API call. Returns the player UUID that owns the claim covering
     * the chunk, or empty if no claim exists.
     */
    static Optional<UUID> getClaimOwnerId(ServerLevel level, ChunkPos pos) {
        IPlayerChunkClaimAPI claim = lookupClaim(level, pos);
        return claim != null ? Optional.ofNullable(claim.getPlayerId()) : Optional.empty();
    }

    /**
     * Shared lookup against the server claims manager. Returns null for unclaimed
     * chunks, matching the OPaC API's own null-as-absent convention.
     */
    private static IPlayerChunkClaimAPI lookupClaim(ServerLevel level, ChunkPos pos) {
        return OpenPACServerAPI.get(level.getServer())
                .getServerClaimsManager()
                .get(level.dimension().location(), pos);
    }
}
