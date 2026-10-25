package dev.breezes.settlements.domain.ai.navigation;

import dev.breezes.settlements.domain.genetics.GeneticMultiplierResolver;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;
import java.util.Arrays;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgilitySpeedResolver {

    private static final float AGILITY_IMPACT = 0.2F;
    private static final float MIN_MODIFIER = 0.1F;

    // PANIC moves at sprint speed and is told apart by the villager's state, so speed never identifies it
    private static final NavigationType[] SPEED_DERIVABLE_TYPES = Arrays.stream(NavigationType.values())
            .filter(type -> type != NavigationType.PANIC)
            .toArray(NavigationType[]::new);

    public static float resolve(@Nonnull NavigationType type, double agility) {
        float multiplier = (float) GeneticMultiplierResolver.centeredMultiplier(agility, AGILITY_IMPACT);
        return Math.max(MIN_MODIFIER, type.getBaseModifier() * multiplier);
    }

    /**
     * Returns the navigation type whose speed at the given agility is nearest the speed modifier.
     * Excludes any types that are not purely speed derivable.
     */
    public static NavigationType resolveNearestNavigationType(float speedModifier, double agility) {
        NavigationType nearestType = SPEED_DERIVABLE_TYPES[0];
        float nearestDistance = Float.MAX_VALUE;
        for (NavigationType type : SPEED_DERIVABLE_TYPES) {
            float distance = Math.abs(resolve(type, agility) - speedModifier);
            if (distance < nearestDistance) {
                nearestType = type;
                nearestDistance = distance;
            }
        }
        return nearestType;
    }

}
