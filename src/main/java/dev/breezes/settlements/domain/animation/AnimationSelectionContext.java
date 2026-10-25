package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ItemCategory;

import javax.annotation.Nonnull;

public record AnimationSelectionContext(ItemCategory mainHandCategory) {

    private static final AnimationSelectionContext[] BY_CATEGORY = contextsByCategory();

    public static AnimationSelectionContext generic() {
        return of(ItemCategory.GENERIC);
    }

    /**
     * The shared context for this main-hand category, returned without allocating.
     */
    public static AnimationSelectionContext of(@Nonnull ItemCategory mainHandCategory) {
        return BY_CATEGORY[mainHandCategory.ordinal()];
    }

    private static AnimationSelectionContext[] contextsByCategory() {
        ItemCategory[] categories = ItemCategory.values();
        AnimationSelectionContext[] contexts = new AnimationSelectionContext[categories.length];
        for (ItemCategory category : categories) {
            contexts[category.ordinal()] = new AnimationSelectionContext(category);
        }
        return contexts;
    }

}
