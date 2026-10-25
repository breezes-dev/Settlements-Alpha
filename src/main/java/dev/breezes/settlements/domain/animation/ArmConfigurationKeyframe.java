package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmPose;

import javax.annotation.Nullable;

/**
 * A clip's per-arm claims at a nonnegative animation tick. A null pose leaves that arm unclaimed.
 */
public record ArmConfigurationKeyframe(int tick, @Nullable ArmPose left, @Nullable ArmPose right) {

    public ArmConfigurationKeyframe {
        if (tick < 0) {
            throw new IllegalArgumentException("Arm configuration keyframe tick must be non-negative");
        }
    }

}
