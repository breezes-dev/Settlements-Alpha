package dev.breezes.settlements.domain.ai.naming;

import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An immutable ordered pool of villager names. Names are stripped, nonblank, and distinct ignoring case; the first
 * spelling of a repeated name keeps its position.
 */
public record VillagerNamePool(List<String> names) {

    public static final VillagerNamePool EMPTY = new VillagerNamePool(List.of());

    public VillagerNamePool {
        Validate.notNull(names, "Villager names must not be null");
        Map<String, String> byLowercase = new LinkedHashMap<>();
        for (String name : names) {
            Validate.notNull(name, "Villager name must not be null");
            String stripped = name.strip();
            if (!stripped.isEmpty()) {
                byLowercase.putIfAbsent(stripped.toLowerCase(Locale.ROOT), stripped);
            }
        }
        names = List.copyOf(byLowercase.values());
    }

    /**
     * Returns the name this pool selects for the UUID, or empty when the pool has no names. The same UUID selects the
     * same name while the pool is unchanged.
     */
    public Optional<String> selectNameForUuid(@Nonnull UUID uuid) {
        if (this.names.isEmpty()) {
            return Optional.empty();
        }

        long mixed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        // UUID bits include Long.MIN_VALUE, whose absolute value is still negative
        int index = Math.floorMod(mixed, this.names.size());
        return Optional.of(this.names.get(index));
    }

}
