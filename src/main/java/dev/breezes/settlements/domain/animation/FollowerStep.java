package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;

/**
 * Mutable interval between animation samples, initially in the reset state.
 */
final class FollowerStep {

    /**
     * The longest step, in ticks; a longer interval up to the reset interval steps this far.
     */
    static final float MAX_STEP_TICKS = 0.1F * ClockTicks.TICKS_PER_SECOND;

    /**
     * The longest interval, in ticks, that steps rather than resets.
     */
    static final float RESET_INTERVAL_TICKS = 1.0F * ClockTicks.TICKS_PER_SECOND;

    private boolean measured;
    private long previousAgeTicks;
    private float previousPartialTicks;
    private boolean resets = true;
    private float stepTicks;

    /**
     * Measures from the previous sample key and records the supplied key as the next origin.
     * <p>
     * The first measurement, an explicit reset, and an interval above RESET_INTERVAL_TICKS or NaN produce a reset.
     * Otherwise, elapsed time is capped at MAX_STEP_TICKS. An earlier key still replaces the previous key but
     * yields zero elapsed time.
     */
    void measure(long ageTicks, float partialTicks, boolean resetRequested) {
        float intervalTicks = (ageTicks - this.previousAgeTicks) + (partialTicks - this.previousPartialTicks);
        // Negated so a NaN interval resets rather than stepping
        this.resets = !this.measured || resetRequested || !(intervalTicks <= RESET_INTERVAL_TICKS);
        this.stepTicks = this.resets ? 0.0F : Math.clamp(intervalTicks, 0.0F, MAX_STEP_TICKS);
        this.measured = true;
        this.previousAgeTicks = ageTicks;
        this.previousPartialTicks = partialTicks;
    }

    /**
     * Returns the fraction of the gap to a constant target retained over this step: zero for a reset and one for zero
     * elapsed time. Within rounding, factors for adjacent, uncapped steps without resets multiply to the factor for
     * the total elapsed time.
     *
     * @param timeConstantTicks the follower's time constant in ticks, positive; not validated
     */
    float decayFactor(float timeConstantTicks) {
        if (this.resets) {
            return 0.0F;
        }
        return (float) Math.exp(-this.stepTicks / timeConstantTicks);
    }

}
