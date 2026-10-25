package dev.breezes.settlements.domain.animation;

import lombok.Getter;

/**
 * Mutable value that approaches a target by retaining a supplied share of the gap.
 */
final class ExponentialFollower {

    /**
     * The widest gap, in radians, the angular form closes by following rather than by jumping.
     */
    static final float TELEPORT_TURN_RADIANS = (float) Math.toRadians(120.0D);

    private static final float HALF_TURN_RADIANS = (float) Math.PI;
    private static final float FULL_TURN_RADIANS = (float) (2.0D * Math.PI);

    private final boolean angular;

    /**
     * The followed value, zero before the first follow.
     */
    @Getter
    private float value;

    private ExponentialFollower(boolean angular) {
        this.angular = angular;
    }

    /**
     * Creates a follower of a linear value.
     */
    static ExponentialFollower linear() {
        return new ExponentialFollower(false);
    }

    /**
     * Creates a follower of an angle in radians. It follows the shortest angular gap and keeps its value within half
     * a turn of zero.
     */
    static ExponentialFollower angular() {
        return new ExponentialFollower(true);
    }

    /**
     * Moves toward the target, retaining the decay factor's share of the gap, and returns whether it jumped directly
     * to the target. It jumps for a nonpositive or NaN decay factor, a nonfinite current value, or an angular gap
     * larger than TELEPORT_TURN_RADIANS. Otherwise, a decay factor at or above one leaves the value unchanged.
     *
     * @param target finite target value, in radians for an angular follower; not validated
     */
    boolean follow(float target, float decayFactor) {
        float gap = this.angular ? wrapRadians(target - this.value) : target - this.value;
        // Negated so a NaN decay factor jumps rather than poisoning the value
        boolean jumps = !(decayFactor > 0.0F)
                || !Float.isFinite(this.value)
                || (this.angular && Math.abs(gap) > TELEPORT_TURN_RADIANS);
        if (jumps) {
            this.value = this.angular ? wrapRadians(target) : target;
            return true;
        }
        // Retain a fraction of the gap so successive steps multiply their decay factors for a constant target
        float followed = target - gap * Math.min(decayFactor, 1.0F);
        this.value = this.angular ? wrapRadians(followed) : followed;
        return false;
    }

    /**
     * Returns the equivalent angle in radians within half a turn either side of zero.
     */
    static float wrapRadians(float radians) {
        return radians - FULL_TURN_RADIANS * (float) Math.floor((radians + HALF_TURN_RADIANS) / FULL_TURN_RADIANS);
    }

}
