package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanternAnimatorTest {

    private static final float DELTA = 0.0001F;
    private static final int GATE_DELAY_TICKS = 8;
    private static final int ARMS_CROSSED_ROTATION =
            VillagerAnimationRig.RIG.indexOf(AnimationTargets.ARMS_CROSSED_ROTATION);

    @Test
    void rejectsANegativeGateDelay() {
        // Arrange
        int negativeGateDelay = -1;

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> new LanternAnimator(negativeGateDelay));
    }

    @Test
    void remainsHidden_untilTheFirstDecidedObservation() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);

        // Act
        for (long tick = 0; tick <= GATE_DELAY_TICKS + InteractAnimations.INTERACT_DURATION_TICKS; tick++) {
            animator.update(tick, 0.0F, false, true, false);
        }

        // Assert
        long observedTick = GATE_DELAY_TICKS + InteractAnimations.INTERACT_DURATION_TICKS;
        assertFalse(animator.isVisible(observedTick, 0.5F));
        assertEquals(0.0F, carryPitch(animator, observedTick, 0.5F), DELTA);
    }

    @Test
    void holdsImmediately_onFirstSightDespiteTheGateDelay() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);

        // Act
        animator.update(0L, 0.0F, true, true, false);

        // Assert
        assertTrue(animator.isVisible(0L, 0.0F));
        assertEquals(holdPitch(), carryPitch(animator, 0L, 0.0F), DELTA);
    }

    @Test
    void holdsImmediately_whenEarlierObservationsWereUndecided() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);
        for (long tick = 0; tick < GATE_DELAY_TICKS; tick++) {
            animator.update(tick, 0.0F, false, false, false);
        }

        // Act
        animator.update(GATE_DELAY_TICKS, 0.0F, true, true, false);

        // Assert
        assertTrue(animator.isVisible(GATE_DELAY_TICKS, 0.0F));
        assertEquals(holdPitch(), carryPitch(animator, GATE_DELAY_TICKS, 0.0F), DELTA);
    }

    @Test
    void firstSightWithAYield_startsWithoutALanternOrHoldPose() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);

        // Act
        animator.update(0L, 0.0F, true, true, true);

        // Assert
        assertFalse(animator.isVisible(0L, 0.0F));
        assertEquals(0.0F, carryPitch(animator, 0L, 0.0F), DELTA);
    }

    @Test
    void showsAtTheFractionalSamplePeak_afterTheChangedGateCommits() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);
        animator.update(0L, 0.0F, true, false, false);
        long requestTick = 1L;
        long showTick = requestTick + GATE_DELAY_TICKS;

        // Act
        updateThrough(animator, requestTick, showTick - 1, true, false);
        assertEquals(0.0F, carryPitch(animator, showTick - 1, 0.0F), DELTA);
        updateThrough(animator, showTick, showTick + InteractAnimations.INTERACT_PEAK_TICK - 1, true, false);

        // Assert
        long tickBeforePeak = showTick + InteractAnimations.INTERACT_PEAK_TICK - 1;
        assertFalse(animator.isVisible(tickBeforePeak, 0.99F));
        assertTrue(animator.isVisible(tickBeforePeak, 1.0F));
        assertEquals(peakPitch(), carryPitch(animator, tickBeforePeak, 1.0F), DELTA);
    }

    @Test
    void stowKeepsTheLanternVisible_untilTheFractionalSamplePeak() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);
        animator.update(0L, 0.0F, true, true, false);
        long requestTick = 1L;
        long stowTick = requestTick + GATE_DELAY_TICKS;

        // Act
        updateThrough(animator, requestTick, stowTick + InteractAnimations.INTERACT_PEAK_TICK - 1, false, false);

        // Assert
        long tickBeforePeak = stowTick + InteractAnimations.INTERACT_PEAK_TICK - 1;
        assertTrue(animator.isVisible(stowTick, 0.0F));
        assertEquals(holdPitch(), carryPitch(animator, stowTick, 0.0F), DELTA);
        assertTrue(animator.isVisible(tickBeforePeak, 0.99F));
        assertFalse(animator.isVisible(tickBeforePeak, 1.0F));
        assertEquals(peakPitch(), carryPitch(animator, tickBeforePeak, 1.0F), DELTA);
    }

    @Test
    void finishesTheShow_beforeStartingAStowThatCommittedMidGesture() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, false, false);
        long showTick = 1L;
        animator.update(showTick, 0.0F, true, true, false);
        long showEndTick = showTick + InteractAnimations.INTERACT_DURATION_TICKS;

        // Act
        updateThrough(animator, showTick + 1, showEndTick, false, false);

        // Assert
        assertTrue(animator.isVisible(showEndTick, 0.0F));
        assertEquals(holdPitch(), carryPitch(animator, showEndTick, 0.0F), DELTA);
        updateThrough(animator, showEndTick + 1, showEndTick + InteractAnimations.INTERACT_PEAK_TICK, false, false);
        assertFalse(animator.isVisible(showEndTick + InteractAnimations.INTERACT_PEAK_TICK, 0.0F));
    }

    @Test
    void finishesTheStow_beforeStartingAShowThatCommittedMidGesture() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, true, false);
        long stowTick = 1L;
        animator.update(stowTick, 0.0F, true, false, false);
        long stowEndTick = stowTick + InteractAnimations.INTERACT_DURATION_TICKS;

        // Act
        updateThrough(animator, stowTick + 1, stowEndTick, true, false);

        // Assert
        assertFalse(animator.isVisible(stowEndTick, 0.0F));
        assertEquals(0.0F, carryPitch(animator, stowEndTick, 0.0F), DELTA);
        updateThrough(animator, stowEndTick + 1, stowEndTick + InteractAnimations.INTERACT_PEAK_TICK, true, false);
        assertTrue(animator.isVisible(stowEndTick + InteractAnimations.INTERACT_PEAK_TICK, 0.0F));
    }

    @Test
    void yieldHidesImmediately_andFadesTheHoldToRest() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, true, false);
        long yieldTick = 1L;

        // Act
        animator.update(yieldTick, 0.0F, true, true, true);

        // Assert
        assertFalse(animator.isVisible(yieldTick, 0.0F));
        assertEquals(holdPitch(), carryPitch(animator, yieldTick, 0.0F), DELTA);
        long halfwayTick = yieldTick + LanternAnimator.YIELD_FADE_TICKS / 2;
        updateThrough(animator, yieldTick + 1, halfwayTick, true, true);
        float halfwayPitch = carryPitch(animator, halfwayTick, 0.0F);
        assertTrue(Math.abs(halfwayPitch) > DELTA);
        assertTrue(Math.abs(halfwayPitch) < Math.abs(holdPitch()));
        updateThrough(animator, halfwayTick + 1, yieldTick + LanternAnimator.YIELD_FADE_TICKS, true, true);
        assertEquals(0.0F, carryPitch(animator, yieldTick + LanternAnimator.YIELD_FADE_TICKS, 0.0F), DELTA);
    }

    @Test
    void yieldFadesFromTheInterruptedShowPose_withoutASnap() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, false, false);
        long showTick = 1L;
        long yieldTick = showTick + InteractAnimations.INTERACT_PEAK_TICK / 2;
        updateThrough(animator, showTick, yieldTick - 1, true, false);
        float interruptedPitch = carryPitch(animator, yieldTick, 0.0F);

        // Act
        animator.update(yieldTick, 0.0F, true, true, true);

        // Assert
        assertTrue(Math.abs(interruptedPitch) > DELTA);
        assertFalse(animator.isVisible(yieldTick, 0.0F));
        assertEquals(interruptedPitch, carryPitch(animator, yieldTick, 0.0F), DELTA);
        updateThrough(animator, yieldTick + 1, yieldTick + LanternAnimator.YIELD_FADE_TICKS, true, true);
        assertEquals(0.0F, carryPitch(animator, yieldTick + LanternAnimator.YIELD_FADE_TICKS, 0.0F), DELTA);
    }

    @Test
    void yieldFadesFromTheInterruptedStowPose_withoutASnap() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, true, false);
        long stowTick = 1L;
        long yieldTick = stowTick + InteractAnimations.INTERACT_PEAK_TICK / 2;
        updateThrough(animator, stowTick, yieldTick - 1, false, false);
        float interruptedPitch = carryPitch(animator, yieldTick, 0.0F);

        // Act
        animator.update(yieldTick, 0.0F, true, false, true);

        // Assert
        assertFalse(animator.isVisible(yieldTick, 0.0F));
        assertEquals(interruptedPitch, carryPitch(animator, yieldTick, 0.0F), DELTA);
        long halfwayTick = yieldTick + LanternAnimator.YIELD_FADE_TICKS / 2;
        updateThrough(animator, yieldTick + 1, halfwayTick, false, true);
        float halfwayPitch = carryPitch(animator, halfwayTick, 0.0F);
        assertTrue(Math.abs(halfwayPitch) < Math.abs(interruptedPitch));
    }

    @Test
    void yieldAtAFractionalSample_fadesFromThatExactPoseAndFraction() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, false, false);
        long showTick = 1L;
        long yieldTick = showTick + InteractAnimations.INTERACT_PEAK_TICK / 2;
        updateThrough(animator, showTick, yieldTick - 1, true, false);
        animator.update(yieldTick, 0.74F, true, true, false);
        float interruptedPitch = carryPitch(animator, yieldTick, 0.75F);

        // Act
        animator.update(yieldTick, 0.75F, true, true, true);

        // Assert
        assertFalse(animator.isVisible(yieldTick, 0.75F));
        assertEquals(interruptedPitch, carryPitch(animator, yieldTick, 0.75F), DELTA);
        assertTrue(Math.abs(carryPitch(animator, yieldTick, 0.76F)) < Math.abs(interruptedPitch));
        long fadeEndTick = yieldTick + LanternAnimator.YIELD_FADE_TICKS;
        updateThrough(animator, yieldTick + 1, fadeEndTick, true, true);
        assertTrue(Math.abs(carryPitch(animator, fadeEndTick, 0.0F)) > DELTA);
        assertEquals(0.0F, carryPitch(animator, fadeEndTick, 0.75F), DELTA);
    }

    @Test
    void returnsWithTheShowGesture_afterAnUninterruptedSettle() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(GATE_DELAY_TICKS);
        animator.update(0L, 0.0F, true, true, false);
        animator.update(1L, 0.0F, true, true, true);
        long clearTick = 2L;
        long returnTick = clearTick + LanternAnimator.RETURN_SETTLE_TICKS;

        // Act
        updateThrough(animator, clearTick, returnTick, true, false);

        // Assert
        assertFalse(animator.isVisible(returnTick, 0.0F));
        assertEquals(0.0F, carryPitch(animator, returnTick, 0.0F), DELTA);
        long halfwayTick = returnTick + InteractAnimations.INTERACT_PEAK_TICK / 2;
        updateThrough(animator, returnTick + 1, halfwayTick, true, false);
        assertTrue(Math.abs(carryPitch(animator, halfwayTick, 0.0F)) > DELTA);
        updateThrough(animator, halfwayTick + 1, returnTick + InteractAnimations.INTERACT_PEAK_TICK, true, false);
        assertTrue(animator.isVisible(returnTick + InteractAnimations.INTERACT_PEAK_TICK, 0.0F));
    }

    @Test
    void restartsTheSettle_whenAYieldReturnsBeforeItEnds() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, true, false);
        animator.update(1L, 0.0F, true, true, true);
        long firstClearTick = 2L;
        long repeatedYieldTick = firstClearTick + LanternAnimator.RETURN_SETTLE_TICKS - 1;
        updateThrough(animator, firstClearTick, repeatedYieldTick - 1, true, false);
        animator.update(repeatedYieldTick, 0.0F, true, true, true);
        long secondClearTick = repeatedYieldTick + 1;
        long returnTick = secondClearTick + LanternAnimator.RETURN_SETTLE_TICKS;

        // Act
        updateThrough(animator, secondClearTick, returnTick - 1, true, false);

        // Assert
        assertFalse(animator.isVisible(returnTick - 1, 0.0F));
        assertEquals(0.0F, carryPitch(animator, returnTick - 1, 0.0F), DELTA);
        updateThrough(animator, returnTick, returnTick + InteractAnimations.INTERACT_PEAK_TICK, true, false);
        assertTrue(animator.isVisible(returnTick + InteractAnimations.INTERACT_PEAK_TICK, 0.0F));
    }

    @Test
    void doesNotReturn_whenTheRequestHasClosedButTheCommittedGateRemainsOpen() {
        // Arrange
        int longGateDelay = LanternAnimator.RETURN_SETTLE_TICKS + InteractAnimations.INTERACT_DURATION_TICKS * 2;
        LanternAnimator animator = new LanternAnimator(longGateDelay);
        animator.update(0L, 0.0F, true, true, false);
        animator.update(1L, 0.0F, true, true, true);
        long clearTick = 2L;
        long observedTick = clearTick + LanternAnimator.RETURN_SETTLE_TICKS + InteractAnimations.INTERACT_PEAK_TICK;

        // Act
        updateThrough(animator, clearTick, observedTick, false, false);

        // Assert
        assertFalse(animator.isVisible(observedTick, 0.0F));
        assertEquals(0.0F, carryPitch(animator, observedTick, 0.0F), DELTA);
    }

    @Test
    void tracksGateChangesDuringAYield_beforeAllowingAReturn() {
        // Arrange
        int longGateDelay = LanternAnimator.RETURN_SETTLE_TICKS + InteractAnimations.INTERACT_DURATION_TICKS * 2;
        LanternAnimator animator = new LanternAnimator(longGateDelay);
        animator.update(0L, 0.0F, true, true, false);
        long yieldTick = 1L;
        updateThrough(animator, yieldTick, yieldTick + longGateDelay, false, true);
        long clearTick = yieldTick + longGateDelay + 1;
        long beforeCommit = clearTick + longGateDelay - 1;

        // Act
        updateThrough(animator, clearTick, beforeCommit, true, false);

        // Assert
        assertFalse(animator.isVisible(beforeCommit, 0.0F));
        assertEquals(0.0F, carryPitch(animator, beforeCommit, 0.0F), DELTA);
        long showTick = clearTick + longGateDelay;
        updateThrough(animator, showTick, showTick + InteractAnimations.INTERACT_PEAK_TICK, true, false);
        assertTrue(animator.isVisible(showTick + InteractAnimations.INTERACT_PEAK_TICK, 0.0F));
    }

    @Test
    void repeatedUpdatesAtTheSameTick_doNotRestartTheYieldFade() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, true, false);
        long yieldTick = 1L;
        animator.update(yieldTick, 0.0F, true, true, true);
        float firstPitch = carryPitch(animator, yieldTick, 0.5F);

        // Act
        animator.update(yieldTick, 0.0F, true, true, true);

        // Assert
        assertFalse(animator.isVisible(yieldTick, 0.5F));
        assertEquals(firstPitch, carryPitch(animator, yieldTick, 0.5F), DELTA);
    }

    @Test
    void futureReads_doNotCompleteOrReverseALiveGesture() {
        // Arrange
        LanternAnimator animator = new LanternAnimator(0);
        animator.update(0L, 0.0F, true, false, false);
        long showTick = 1L;
        animator.update(showTick, 0.0F, true, true, false);
        float initialPitch = carryPitch(animator, showTick, 0.5F);

        // Act
        animator.isVisible(showTick + InteractAnimations.INTERACT_DURATION_TICKS, 0.0F);
        carryPitch(animator, showTick + InteractAnimations.INTERACT_DURATION_TICKS, 0.0F);
        animator.update(showTick + 1, 0.0F, true, false, false);

        // Assert
        assertEquals(initialPitch, carryPitch(animator, showTick, 0.5F), DELTA);
        assertTrue(animator.isVisible(showTick + InteractAnimations.INTERACT_PEAK_TICK, 0.0F));
    }

    private static void updateThrough(LanternAnimator animator,
                                      long fromTick,
                                      long toTick,
                                      boolean requested,
                                      boolean yielding) {
        for (long tick = fromTick; tick <= toTick; tick++) {
            animator.update(tick, 0.0F, true, requested, yielding);
        }
    }

    private static float carryPitch(LanternAnimator animator, long tick, float partialTicks) {
        AnimationContribution contribution = new AnimationContribution(VillagerAnimationRig.RIG);
        animator.contributeCarry(contribution, tick, partialTicks);
        return contribution.value(ARMS_CROSSED_ROTATION, 0);
    }

    private static float holdPitch() {
        return (float) Math.toRadians(LanternCarryAnimations.HOLD_PITCH_DEGREES);
    }

    private static float peakPitch() {
        return (float) Math.toRadians(InteractAnimations.INTERACT_PEAK_PITCH_DEGREES);
    }

}
