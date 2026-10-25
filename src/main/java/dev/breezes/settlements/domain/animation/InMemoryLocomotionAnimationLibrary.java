package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import lombok.Builder;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

public final class InMemoryLocomotionAnimationLibrary implements LocomotionAnimationLibrary {

    private final KeyframeAnimation standingPose;
    private final Map<NavigationType, KeyframeAnimation> animationsByType;

    @Builder
    private InMemoryLocomotionAnimationLibrary(@Nonnull KeyframeAnimation standingPose,
                                               @Nonnull Map<NavigationType, KeyframeAnimation> animations) {
        this.standingPose = standingPose;
        this.animationsByType = new EnumMap<>(NavigationType.class);
        this.animationsByType.putAll(animations);
    }

    @Override
    public KeyframeAnimation standingPose() {
        return this.standingPose;
    }

    @Override
    public KeyframeAnimation resolve(@Nonnull NavigationType navigationType) {
        KeyframeAnimation animation = this.animationsByType.get(navigationType);
        if (animation == null) {
            throw new IllegalArgumentException("Missing locomotion animation for " + navigationType);
        }
        return animation;
    }

}
