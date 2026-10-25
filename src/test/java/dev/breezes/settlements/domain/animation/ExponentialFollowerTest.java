package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExponentialFollowerTest {

    private static final float TOLERANCE = 1.0E-5F;

    // Slow enough to expose differences between the sampled frame rates without making individual steps negligible
    private static final float TIME_CONSTANT_TICKS = 2.0F;

    private static final List<Supplier<ExponentialFollower>> FORMS =
            List.of(ExponentialFollower::linear, ExponentialFollower::angular);

    private static final long START_AGE_TICKS = 1_000L;
    private static final float START_VALUE = 0.25F;
    private static final float TARGET = 1.5F;

    @Test
    void follow_aConstantTarget_landsInOnePlace_howeverAnUncappedIntervalIsSplit() {
        // Arrange: one interval of the longest step, taken whole, in eight even steps, and in uneven ones. A blend factor
        // fixed per step would leave each split somewhere else.
        float interval = FollowerStep.MAX_STEP_TICKS;
        float[] whole = {interval};
        float[] even = new float[8];
        Arrays.fill(even, interval / 8.0F);
        float[] uneven = {interval * 0.05F, interval * 0.35F, interval * 0.1F, interval * 0.5F};
        float expected = TARGET - (TARGET - START_VALUE) * (float) Math.exp(-interval / TIME_CONSTANT_TICKS);

        for (Supplier<ExponentialFollower> form : FORMS) {
            // Act
            float wholeValue = followInSteps(form.get(), whole);
            float evenValue = followInSteps(form.get(), even);
            float unevenValue = followInSteps(form.get(), uneven);

            // Assert
            assertEquals(expected, wholeValue, TOLERANCE);
            assertEquals(wholeValue, evenValue, TOLERANCE);
            assertEquals(wholeValue, unevenValue, TOLERANCE);
        }
    }

    @Test
    void follow_aSteadilyTurningTarget_agreesAcrossFrameRates_withinAThirdOfItsLag() {
        // Arrange: a continuous follower's steady-turn lag is the turn rate times the time constant. A fixed decay
        // factor per frame would make that lag depend on frame rate.
        float turnRadiansPerTick = 0.05F;
        int durationTicks = ClockTicks.TICKS_PER_SECOND;
        float continuousLag = turnRadiansPerTick * TIME_CONSTANT_TICKS;
        float continuous = turnRadiansPerTick * durationTicks
                - continuousLag * (1.0F - (float) Math.exp(-durationTicks / TIME_CONSTANT_TICKS));

        for (int framesPerSecond : new int[]{20, 60, 144}) {
            ExponentialFollower follower = ExponentialFollower.angular();
            FollowerStep step = new FollowerStep();
            int frames = framesPerSecond * durationTicks / ClockTicks.TICKS_PER_SECOND;

            // Act
            for (int frame = 0; frame <= frames; frame++) {
                float ticks = (float) frame * durationTicks / frames;
                long wholeTicks = (long) Math.floor(ticks);
                step.measure(START_AGE_TICKS + wholeTicks, ticks - wholeTicks, false);
                follower.follow(turnRadiansPerTick * ticks, step.decayFactor(TIME_CONSTANT_TICKS));
            }

            // Assert
            assertEquals(continuous, follower.getValue(), continuousLag / 3.0F, framesPerSecond + " fps");
        }
    }

    @Test
    void follow_acrossTheHalfTurnWrap_closesTheShortGap_withoutTurningTheLongWay() {
        // Arrange: a degree short of the wrap, with the target a degree past it
        ExponentialFollower follower = ExponentialFollower.angular();
        float justShort = (float) Math.toRadians(179.0D);
        float justPast = (float) Math.toRadians(-179.0D);
        follower.follow(justShort, 0.0F);

        // Act
        follower.follow(justPast, 0.5F);

        // Assert: blending the raw angles would carry the value back through zero
        float remainingGap = ExponentialFollower.wrapRadians(justPast - follower.getValue());
        assertEquals((float) Math.toRadians(1.0D), remainingGap, TOLERANCE);
        assertTrue(Math.abs(follower.getValue()) > justShort, "the value turned the long way");
        assertTrue(Math.abs(follower.getValue()) <= (float) Math.PI + TOLERANCE, "the value left the half turn");
    }

    @Test
    void follow_angular_jumpsOntoATargetPastATeleportSizedTurn_andFollowsOneShortOfIt() {
        // Arrange
        ExponentialFollower jumping = ExponentialFollower.angular();
        ExponentialFollower following = ExponentialFollower.angular();
        jumping.follow(0.0F, 0.0F);
        following.follow(0.0F, 0.0F);
        float pastTheTurn = ExponentialFollower.TELEPORT_TURN_RADIANS + 0.05F;
        float shortOfTheTurn = ExponentialFollower.TELEPORT_TURN_RADIANS - 0.05F;

        // Act
        boolean jumped = jumping.follow(pastTheTurn, 0.5F);
        boolean followed = !following.follow(shortOfTheTurn, 0.5F);

        // Assert: interpolating a teleport-sized turn would animate a head turn across the discontinuity
        assertTrue(jumped);
        assertEquals(pastTheTurn, jumping.getValue(), TOLERANCE);
        assertTrue(followed);
        assertEquals(shortOfTheTurn * 0.5F, following.getValue(), TOLERANCE);
    }

    @Test
    void follow_linear_followsAnyDistance() {
        // Arrange: a linear follower must interpolate even when the gap exceeds the angular teleport threshold
        ExponentialFollower follower = ExponentialFollower.linear();
        follower.follow(0.0F, 0.0F);
        float farAway = 10.0F * ExponentialFollower.TELEPORT_TURN_RADIANS;

        // Act
        boolean jumped = follower.follow(farAway, 0.5F);

        // Assert
        assertFalse(jumped);
        assertEquals(farAway * 0.5F, follower.getValue(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0F, -1.0F, Float.NaN})
    void follow_withAFactorOfZeroOrLessOrNaN_landsOnTheTarget(float decayFactor) {
        for (Supplier<ExponentialFollower> form : FORMS) {
            // Arrange
            ExponentialFollower follower = form.get();
            follower.follow(START_VALUE, 0.0F);

            // Act
            boolean jumped = follower.follow(TARGET, decayFactor);

            // Assert: a reset that kept any of the old value would leave a follower short of its target
            assertTrue(jumped);
            assertEquals(TARGET, follower.getValue(), 0.0F);
        }
    }

    @Test
    void follow_fromAValueThatIsNotFinite_landsOnTheTarget() {
        for (Supplier<ExponentialFollower> form : FORMS) {
            // Arrange: seed a non-finite value to exercise recovery on the next finite target
            ExponentialFollower follower = form.get();
            follower.follow(Float.NaN, 0.0F);

            // Act
            follower.follow(TARGET, 0.5F);

            // Assert: blending from NaN would keep it NaN forever
            assertEquals(TARGET, follower.getValue(), 0.0F);
        }
    }

    /**
     * Returns the followed value after sampling a constant target over the supplied tick intervals.
     */
    private static float followInSteps(ExponentialFollower follower, float[] stepTicks) {
        FollowerStep step = new FollowerStep();
        step.measure(START_AGE_TICKS, 0.0F, false);
        follower.follow(START_VALUE, step.decayFactor(TIME_CONSTANT_TICKS));
        double elapsedTicks = 0.0D;
        for (float stepLength : stepTicks) {
            elapsedTicks += stepLength;
            long wholeTicks = (long) Math.floor(elapsedTicks);
            step.measure(START_AGE_TICKS + wholeTicks, (float) (elapsedTicks - wholeTicks), false);
            follower.follow(TARGET, step.decayFactor(TIME_CONSTANT_TICKS));
        }
        return follower.getValue();
    }

}
