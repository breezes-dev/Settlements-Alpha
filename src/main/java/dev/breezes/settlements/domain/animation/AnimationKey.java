package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ItemCategory;

import javax.annotation.Nonnull;

public record AnimationKey(AnimationArchetype archetype,
                           ItemCategory category) {

    private static final AnimationKey[][] BY_ARCHETYPE_AND_CATEGORY = keysByArchetypeAndCategory();

    /**
     * The shared key for this pair.
     * <p>
     * Returns without allocating anything.
     */
    public static AnimationKey of(@Nonnull AnimationArchetype archetype, @Nonnull ItemCategory category) {
        return BY_ARCHETYPE_AND_CATEGORY[archetype.ordinal()][category.ordinal()];
    }

    private static AnimationKey[][] keysByArchetypeAndCategory() {
        AnimationArchetype[] archetypes = AnimationArchetype.values();
        ItemCategory[] categories = ItemCategory.values();
        AnimationKey[][] keys = new AnimationKey[archetypes.length][categories.length];
        for (AnimationArchetype archetype : archetypes) {
            for (ItemCategory category : categories) {
                keys[archetype.ordinal()][category.ordinal()] = new AnimationKey(archetype, category);
            }
        }
        return keys;
    }

}
