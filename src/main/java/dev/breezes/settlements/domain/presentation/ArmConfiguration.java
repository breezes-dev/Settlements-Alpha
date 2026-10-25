package dev.breezes.settlements.domain.presentation;

import javax.annotation.Nonnull;

/**
 * A discrete geometry choice for each arm independently.
 */
public record ArmConfiguration(ArmPose left, ArmPose right) {

    public static final ArmConfiguration BOTH_CROSSED = new ArmConfiguration(ArmPose.CROSSED, ArmPose.CROSSED);
    public static final ArmConfiguration BOTH_STRAIGHT = new ArmConfiguration(ArmPose.STRAIGHT, ArmPose.STRAIGHT);
    public static final ArmConfiguration LEFT_CROSSED_RIGHT_STRAIGHT = new ArmConfiguration(ArmPose.CROSSED, ArmPose.STRAIGHT);
    public static final ArmConfiguration RIGHT_CROSSED_LEFT_STRAIGHT = new ArmConfiguration(ArmPose.STRAIGHT, ArmPose.CROSSED);

    /**
     * Returns the shared configuration for this pair of poses without allocating a new instance.
     */
    public static ArmConfiguration of(@Nonnull ArmPose left, @Nonnull ArmPose right) {
        if (left == ArmPose.CROSSED) {
            return right == ArmPose.CROSSED ? BOTH_CROSSED : LEFT_CROSSED_RIGHT_STRAIGHT;
        }
        return right == ArmPose.CROSSED ? RIGHT_CROSSED_LEFT_STRAIGHT : BOTH_STRAIGHT;
    }

}
