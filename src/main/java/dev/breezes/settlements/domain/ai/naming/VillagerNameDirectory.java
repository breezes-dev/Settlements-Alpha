package dev.breezes.settlements.domain.ai.naming;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Server-authoritative directory of villagers' current display names, including unloaded villagers.
 * <p>
 * Stored names take precedence over generated fallbacks. Missing entries, including entries removed after death,
 * resolve against the current datapack pool and may change when that pool changes. An empty pool falls back to the
 * UUID string. Resolving a missing entry does not retain it.
 */
public interface VillagerNameDirectory {

    /**
     * Resolves the UUID's stored display name or its current fallback.
     *
     * @param uuid the villager UUID; every caller is expected to hold a real villager identity,
     *             not merely a UUID it hasn't checked for null yet
     */
    String resolve(@Nonnull UUID uuid);

    /**
     * Records the UUID's current display name, overwriting any previous entry.
     */
    void upsert(@Nonnull UUID uuid, @Nonnull String name);

    /**
     * Removes the UUID's entry.
     * <p>
     * Only call this once the villager is actually gone for good -- never for a chunk unload.
     */
    void remove(@Nonnull UUID uuid);

}
