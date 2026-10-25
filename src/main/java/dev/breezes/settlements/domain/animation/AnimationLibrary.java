package dev.breezes.settlements.domain.animation;

import javax.annotation.Nonnull;

public interface AnimationLibrary {

    /**
     * The clip for the archetype in this selection context. Equal arguments return the same instance.
     */
    KeyframeAnimation resolve(@Nonnull AnimationArchetype archetype, @Nonnull AnimationSelectionContext context);

}
