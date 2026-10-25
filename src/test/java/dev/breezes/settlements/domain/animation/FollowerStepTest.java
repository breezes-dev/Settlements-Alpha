package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowerStepTest {

    private static final float TOLERANCE = 1.0E-6F;

    // Keeps positive-step decay factors away from zero and one so interval differences remain measurable
    private static final float TIME_CONSTANT_TICKS = 4.0F;

    private static final long AGE_TICKS = 1_000L;

    // At this age, adding a quarter tick as a float loses the fractional tick
    private static final long LONG_LIVED_AGE_TICKS = 5_000_000L;

    @Test
    void decayFactor_isZero_beforeAnyMeasurement() {
        // Arrange: a fresh step that read as covering no time would leave a follower wherever it last stood
        FollowerStep step = new FollowerStep();

        // Act, Assert
        assertEquals(0.0F, step.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
    }

    @Test
    void decayFactor_isZero_atTheFirstMeasurement_evenWithinTheResetIntervalOfAgeZero() {
        // Arrange: measured from age zero, this first key would read as an ordinary step
        FollowerStep step = new FollowerStep();
        long withinTheResetInterval = (long) (FollowerStep.RESET_INTERVAL_TICKS / 2.0F);

        // Act
        step.measure(withinTheResetInterval, 0.0F, false);

        // Assert
        assertEquals(0.0F, step.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
    }

    @Test
    void decayFactor_coversTheIntervalBetweenTwoMeasurements() {
        // Arrange: a factor fixed per measurement would not depend on the interval
        FollowerStep step = new FollowerStep();
        step.measure(AGE_TICKS, 0.25F, false);

        // Act
        step.measure(AGE_TICKS, 0.75F, false);

        // Assert
        assertEquals((float) Math.exp(-0.5F / TIME_CONSTANT_TICKS), step.decayFactor(TIME_CONSTANT_TICKS), TOLERANCE);
    }

    @Test
    void decayFactor_ofAnIntervalPastTheLongestStep_isTheLongestStepsFactor() {
        // Arrange: between the longest step and the reset interval, so it steps, but capped
        FollowerStep capped = new FollowerStep();
        FollowerStep longest = new FollowerStep();
        capped.measure(AGE_TICKS, 0.0F, false);
        longest.measure(AGE_TICKS, 0.0F, false);
        float pastTheLongestStep = (FollowerStep.MAX_STEP_TICKS + FollowerStep.RESET_INTERVAL_TICKS) / 2.0F;

        // Act
        capped.measure(AGE_TICKS + (long) pastTheLongestStep, 0.0F, false);
        longest.measure(AGE_TICKS + (long) FollowerStep.MAX_STEP_TICKS,
                FollowerStep.MAX_STEP_TICKS - (long) FollowerStep.MAX_STEP_TICKS, false);

        // Assert: an uncapped interval would produce a smaller decay factor
        assertTrue(longest.decayFactor(TIME_CONSTANT_TICKS) > 0.0F);
        assertEquals(longest.decayFactor(TIME_CONSTANT_TICKS), capped.decayFactor(TIME_CONSTANT_TICKS), TOLERANCE);
    }

    @Test
    void decayFactor_isZero_onlyAfterAnIntervalLongerThanTheResetInterval() {
        // Arrange
        FollowerStep atTheResetInterval = new FollowerStep();
        FollowerStep pastTheResetInterval = new FollowerStep();
        atTheResetInterval.measure(AGE_TICKS, 0.0F, false);
        pastTheResetInterval.measure(AGE_TICKS, 0.0F, false);
        long resetInterval = (long) FollowerStep.RESET_INTERVAL_TICKS;

        // Act
        atTheResetInterval.measure(AGE_TICKS + resetInterval, 0.0F, false);
        pastTheResetInterval.measure(AGE_TICKS + resetInterval, 0.5F, false);

        // Assert: a villager back from out of view would otherwise turn from where it was last seen
        assertTrue(atTheResetInterval.decayFactor(TIME_CONSTANT_TICKS) > 0.0F);
        assertEquals(0.0F, pastTheResetInterval.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
    }

    @Test
    void decayFactor_isZero_whenAResetIsRequested() {
        // Arrange
        FollowerStep step = new FollowerStep();
        step.measure(AGE_TICKS, 0.0F, false);

        // Act: an ordinary interval, which would step without the request
        step.measure(AGE_TICKS + 1, 0.0F, true);

        // Assert
        assertEquals(0.0F, step.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
    }

    @Test
    void decayFactor_isOne_forAKeyBehindTheLastOne() {
        // Arrange
        FollowerStep step = new FollowerStep();
        step.measure(AGE_TICKS, 0.5F, false);

        // Act
        step.measure(AGE_TICKS, 0.25F, false);

        // Assert: a negative step would push a follower away from its target
        assertEquals(1.0F, step.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
    }

    @Test
    void decayFactor_isZero_forAnIntervalThatIsNotANumber() {
        // Arrange
        FollowerStep step = new FollowerStep();
        step.measure(AGE_TICKS, 0.0F, false);

        // Act
        step.measure(AGE_TICKS + 1, Float.NaN, false);

        // Assert: a NaN factor would poison every follower that steps with it
        assertEquals(0.0F, step.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
    }

    @Test
    void measure_keepsAQuarterTick_onALongLivedVillager() {
        // Arrange: adding age and partial tick as floats would lose the quarter-tick difference
        FollowerStep longLived = new FollowerStep();
        FollowerStep young = new FollowerStep();
        longLived.measure(LONG_LIVED_AGE_TICKS, 0.25F, false);
        young.measure(AGE_TICKS, 0.25F, false);

        // Act
        longLived.measure(LONG_LIVED_AGE_TICKS, 0.5F, false);
        young.measure(AGE_TICKS, 0.5F, false);

        // Assert
        assertEquals(young.decayFactor(TIME_CONSTANT_TICKS), longLived.decayFactor(TIME_CONSTANT_TICKS), 0.0F);
        assertEquals((float) Math.exp(-0.25F / TIME_CONSTANT_TICKS), longLived.decayFactor(TIME_CONSTANT_TICKS),
                TOLERANCE);
    }

}
