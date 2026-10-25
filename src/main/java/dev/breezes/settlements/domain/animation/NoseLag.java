package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;

/**
 * Mutable nose lag relative to the drawn head's world heading.
 */
final class NoseLag {

    private static final float TIME_CONSTANT_TICKS = ClockTicks.TICKS_PER_SECOND / 16.0F;

    private static final float ROLL_PER_RADIAN_OF_LAG = 1.2F;

    /**
     * The widest nose roll either way, in radians, for the lag's own roll and for the nose roll's final value.
     */
    static final float MAX_ROLL_RADIANS = 20.0F * Mth.DEG_TO_RAD;

    private static final int NOSE_ROTATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.NOSE_ROTATION);
    private static final int ROLL_AXIS = 2;

    private final ExponentialFollower smoothedHeading = ExponentialFollower.angular();

    /**
     * The most recently followed head heading, in world-space radians.
     */
    private float headWorldHeading;

    /**
     * Advances toward the head frame's world heading under the observation's body orientation.
     * <p>
     * Removes nose lag immediately on a reset.
     */
    void follow(@Nonnull HeadFrame headFrame,
                @Nonnull VillagerAnimationObservation observation,
                @Nonnull FollowerStep step) {
        float modelHeading = headFrame.modelHeadingRadians();
        // Vanilla's upside-down flip mirrors the model's headings in world heading
        this.headWorldHeading = ExponentialFollower.wrapRadians(observation.bodyHeadingRadians()
                + (observation.isUpsideDown() ? -modelHeading : modelHeading));
        // A near-vertical head's heading jumps arbitrarily, so the lag starts over rather than chasing it
        float decayFactor = headFrame.isHeadingDegenerate() ? 0.0F : step.decayFactor(TIME_CONSTANT_TICKS);
        this.smoothedHeading.follow(this.headWorldHeading, decayFactor);
    }

    /**
     * Adds a weight-scaled, bounded lag roll to the pose and clamps its final nose roll regardless of weight.
     *
     * @param pose   pose on the villager rig; not validated
     * @param weight fraction of the lag roll to apply, in [0, 1]; not validated
     */
    void apply(@Nonnull AnimationPose pose, float weight, @Nonnull VillagerAnimationObservation observation) {
        float lag = ExponentialFollower.wrapRadians(this.smoothedHeading.getValue() - this.headWorldHeading);
        // Rolling by the lag swings the hanging nose's tip away from the side the head turns toward, so it trails;
        // upside down, the model is mirrored in world heading, so the same trail needs the opposite roll
        float lagRoll = ROLL_PER_RADIAN_OF_LAG * (observation.isUpsideDown() ? -lag : lag);
        float roll = pose.get(NOSE_ROTATION, ROLL_AXIS)
                + weight * Math.clamp(lagRoll, -MAX_ROLL_RADIANS, MAX_ROLL_RADIANS);
        pose.set(NOSE_ROTATION, ROLL_AXIS, Math.clamp(roll, -MAX_ROLL_RADIANS, MAX_ROLL_RADIANS));
    }

}
