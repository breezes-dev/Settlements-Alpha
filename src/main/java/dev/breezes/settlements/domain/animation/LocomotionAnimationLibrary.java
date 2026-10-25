package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;

import javax.annotation.Nonnull;

public interface LocomotionAnimationLibrary {

    /**
     * The constant posture of a villager standing still.
     */
    KeyframeAnimation standingPose();

    KeyframeAnimation resolve(@Nonnull NavigationType navigationType);

}
