package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;

/**
 * Mutable trail of the observed head look in world coordinates.
 */
final class HeadTrail {

    private static final float TIME_CONSTANT_TICKS = 0.1F * ClockTicks.TICKS_PER_SECOND;

    /**
     * The maximum displayed head yaw to either side of the body's heading, in radians.
     */
    static final float MAX_DISPLAYED_YAW_RADIANS = 85.0F * Mth.DEG_TO_RAD;

    private final ExponentialFollower worldHeading = ExponentialFollower.angular();
    private final ExponentialFollower pitch = ExponentialFollower.linear();

    /**
     * Advances toward the observed look.
     * <p>
     * A reset or a look heading farther than {@link ExponentialFollower#TELEPORT_TURN_RADIANS} from the current
     * trail moves both yaw and pitch directly to the look direction.
     */
    void follow(@Nonnull VillagerAnimationObservation observation, @Nonnull FollowerStep step) {
        float decayFactor = step.decayFactor(TIME_CONSTANT_TICKS);
        // Follow world heading so a body turn under a fixed look does not create yaw lag. Observed upside-down
        // angles are already negated; undo that flip before following in world coordinates
        float lookYaw = flippedIfUpsideDown(observation, observation.getNetHeadYawDegrees() * Mth.DEG_TO_RAD);
        float lookPitch = flippedIfUpsideDown(observation, observation.getHeadPitchDegrees() * Mth.DEG_TO_RAD);
        boolean headingJumped = this.worldHeading.follow(observation.bodyHeadingRadians() + lookYaw, decayFactor);
        // A jump moves the whole head at once rather than leaving its pitch to catch up
        this.pitch.follow(lookPitch, headingJumped ? 0.0F : decayFactor);
    }

    /**
     * Returns the trailed yaw relative to the observation's body heading, in radians, bounded by
     * MAX_DISPLAYED_YAW_RADIANS and negated for an upside-down model.
     */
    float displayedYawRadians(@Nonnull VillagerAnimationObservation observation) {
        float relativeYaw = ExponentialFollower.wrapRadians(this.worldHeading.getValue() - observation.bodyHeadingRadians());
        return flippedIfUpsideDown(observation, Math.clamp(relativeYaw, -MAX_DISPLAYED_YAW_RADIANS, MAX_DISPLAYED_YAW_RADIANS));
    }

    /**
     * Returns the trailed head pitch in radians, negated for an upside-down model.
     */
    float displayedPitchRadians(@Nonnull VillagerAnimationObservation observation) {
        return flippedIfUpsideDown(observation, this.pitch.getValue());
    }

    private static float flippedIfUpsideDown(@Nonnull VillagerAnimationObservation observation, float angle) {
        return observation.isUpsideDown() ? -angle : angle;
    }

}
