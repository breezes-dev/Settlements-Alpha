package dev.breezes.settlements.domain.genetics;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * Resolves an additive size adjustment from a normalized constitution gene.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConstitutionSizeResolver {

    private static final double MIN_SCALE = 0.9;
    // Keep the upper bound at vanilla size to preserve doorway clearance
    private static final double MAX_SCALE = 1.0;
    private static final double BASE_SCALE = 1.0;

    /**
     * Returns the size adjustment relative to the base scale.
     *
     * @param constitution normalized gene value in [0, 1]; not validated or clamped
     */
    public static double resolveBonus(double constitution) {
        double resolvedScale = MIN_SCALE + constitution * (MAX_SCALE - MIN_SCALE);
        return resolvedScale - BASE_SCALE;
    }

}
