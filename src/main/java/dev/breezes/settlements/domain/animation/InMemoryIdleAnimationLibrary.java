package dev.breezes.settlements.domain.animation;

import lombok.Builder;

import javax.annotation.Nonnull;
import java.util.List;

public final class InMemoryIdleAnimationLibrary implements IdleAnimationLibrary {

    private final List<KeyframeAnimation> idleAnimations;

    @Builder
    private InMemoryIdleAnimationLibrary(@Nonnull List<KeyframeAnimation> idleAnimations) {
        this.idleAnimations = List.copyOf(idleAnimations);
    }

    @Override
    public List<KeyframeAnimation> idleAnimations() {
        return this.idleAnimations;
    }

}
