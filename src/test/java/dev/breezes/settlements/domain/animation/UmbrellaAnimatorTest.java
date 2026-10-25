package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UmbrellaAnimatorTest {

    private static final float DELTA = 0.0001F;
    private static final int NO_GATE_DELAY_TICKS = 0;
    // Short enough to keep the per-tick observation loops below readable; the mechanism does not vary with the magnitude.
    private static final int GATE_DELAY_TICKS = 8;

    private static final int ARMS_CROSSED = VillagerAnimationRig.RIG.indexOf(AnimationTargets.ARMS_CROSSED_ROTATION);
    private static final int EAST = UmbrellaAnimationTargets.RIG.indexOf(UmbrellaAnimationTargets.EAST_ROTATION);

    @Test
    void isVisible_isFalseForAFreshlyCreatedAnimator() {
        // Arrange
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);

        // Act, Assert: a villager that has never seen rain carries no umbrella at all.
        assertFalse(animator.isVisible(0L, 0.0F));
    }

    @Test
    void update_showsTheUmbrellaOnTheTickTheGateOpens() {
        // Arrange
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);

        // Act
        animator.update(50L, true);

        // Assert: the attachment appears the same tick the gate flips, not after the carry clip finishes.
        assertTrue(animator.isVisible(50L, 0.0F));
    }

    @Test
    void contributeCarry_movesTheArm_whileTheCanopyStillHoldsItsFurledPose() {
        // Arrange
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);
        animator.update(0L, true);

        // Act: sample partway between raise start and the canopy's own offset tick, where the design
        // requires the arm already travelling while the canopy still holds its furled pose.
        int midTick = UmbrellaCarryAnimations.RAISE_CANOPY_START_TICK / 2;
        float armAtMidTick = carryArmPitch(animator, midTick);
        float eastAtMidTick = canopyEastRoll(animator, midTick);

        // Assert: the carry-raise arm tilt has already left its rest value...
        float armAtRest = clipArmPitch(UmbrellaCarryAnimations.raise(), 0.0F);
        assertTrue(Math.abs(armAtMidTick - armAtRest) > DELTA,
                "expected the carry-raise arm tilt to already be moving before the canopy's offset tick");

        // ...while the canopy is still exactly at its closed (furled) pose, not snapped open early.
        assertEquals(clipEastRoll(UmbrellaAnimations.deploy(), 0.0F), eastAtMidTick, DELTA,
                "expected the canopy to still be furled before its offset tick");
    }

    @Test
    void sampleCanopyInto_opensTheCanopy_oncePastItsOffsetTick() {
        // Arrange
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);
        animator.update(0L, true);

        // Act: sample a couple ticks past the canopy's start offset.
        float eastPastOffsetTick = canopyEastRoll(animator, UmbrellaCarryAnimations.RAISE_CANOPY_START_TICK + 2);

        // Assert
        assertTrue(Math.abs(eastPastOffsetTick - clipEastRoll(UmbrellaAnimations.deploy(), 0.0F)) > DELTA,
                "expected the canopy to have started opening past its offset tick, but it still read the closed pose");
    }

    @Test
    void isVisible_survivesTheWholeLowerAndUndeploySequenceBeforeDisappearing() {
        // Arrange: raise fully, then let the gate drop from a fully-deployed hold.
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);
        animator.update(0L, true);
        long fullyDeployedTick = UmbrellaCarryAnimations.RAISE_DURATION_TICKS + 100L;
        assertTrue(animator.isVisible(fullyDeployedTick, 0.0F));

        // Act
        animator.update(fullyDeployedTick, false);

        // Assert: still visible for the whole lower-and-undeploy, and only gone once it has fully finished.
        long justBeforeFinished = fullyDeployedTick + UmbrellaCarryAnimations.LOWER_DURATION_TICKS - 1;
        long finished = fullyDeployedTick + UmbrellaCarryAnimations.LOWER_DURATION_TICKS;
        assertTrue(animator.isVisible(justBeforeFinished, 0.0F),
                "expected the umbrella to remain visible until the lower-and-undeploy sequence fully finishes");
        assertFalse(animator.isVisible(finished, 0.0F),
                "expected the umbrella to disappear once both the carry and canopy clips finish retracting");
    }

    @Test
    void update_restartsTheOppositeClip_whenTheGateShutsMidRaise() {
        // Arrange: begin raising, and let it travel a quarter of the way up, so the interrupt lands on a
        // pose the raise is visibly partway through rather than on either endpoint.
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);
        animator.update(0L, true);
        long interruptTick = UmbrellaCarryAnimations.RAISE_DURATION_TICKS / 4;

        // Act: rain stops mid-raise.
        animator.update(interruptTick, false);

        // Assert: the lower plays from its own first frame rather than from the pose the raise had
        // reached. The resulting snap is deliberate -- UmbrellaAnimator.update's Javadoc carries why.
        float lowerFirstFrameArm = clipArmPitch(UmbrellaCarryAnimations.lower(), 0.0F);
        assertEquals(lowerFirstFrameArm, carryArmPitch(animator, interruptTick), DELTA,
                "expected an interrupted raise to restart the lower from its first frame");

        // The reversed motion must actually finish retracting in bounded time rather than stalling or
        // resuming the raise.
        assertFalse(animator.isVisible(interruptTick + UmbrellaCarryAnimations.LOWER_DURATION_TICKS + 1, 0.0F),
                "expected the interrupted raise to finish retracting rather than never completing");
    }

    @Test
    void update_isIdempotent_whenTheSameTickIsObservedTwice() {
        // Arrange: a supplementary render pass re-enters every render callback for one frame (platform
        // standard P8), so a second update for the same tick must not progress the state machine any further than a
        // single one would.
        UmbrellaAnimator animator = new UmbrellaAnimator(NO_GATE_DELAY_TICKS);
        long tick = 7L;
        float partialTicks = 0.4F;
        animator.update(tick, true);
        float armAfterFirst = carryArmPitch(animator, tick, partialTicks);
        float eastAfterFirst = canopyEastRoll(animator, tick, partialTicks);
        boolean visibleAfterFirst = animator.isVisible(tick, partialTicks);

        // Act: a second observation of the exact same tick, as a supplementary pass would produce.
        animator.update(tick, true);

        // Assert
        assertEquals(armAfterFirst, carryArmPitch(animator, tick, partialTicks));
        assertEquals(eastAfterFirst, canopyEastRoll(animator, tick, partialTicks));
        assertEquals(visibleAfterFirst, animator.isVisible(tick, partialTicks));
    }

    @Test
    void update_holdsAGateChangeUntilTheGateDelayElapses() {
        // Arrange
        UmbrellaAnimator animator = new UmbrellaAnimator(GATE_DELAY_TICKS);

        // Act, Assert: the gate opens at tick 0 and stays open, and nothing moves until the whole delay
        // has been spent standing on it.
        for (long tick = 0; tick < GATE_DELAY_TICKS; tick++) {
            animator.update(tick, true);
            assertFalse(animator.isVisible(tick, 0.0F),
                    "expected the raise to still be held at tick " + tick + " of the gate delay");
        }

        animator.update(GATE_DELAY_TICKS, true);
        assertTrue(animator.isVisible(GATE_DELAY_TICKS, 0.0F),
                "expected the raise to commit on the tick the gate delay ran out");
    }

    @Test
    void update_delaysTheLowerAsWellAsTheRaise() {
        // Arrange: raise fully and hold, so the lower starts from a settled pose rather than a reversal.
        UmbrellaAnimator animator = new UmbrellaAnimator(GATE_DELAY_TICKS);
        updateThrough(animator, 0L, GATE_DELAY_TICKS, true);
        long fullyDeployedTick = GATE_DELAY_TICKS + UmbrellaCarryAnimations.RAISE_DURATION_TICKS + 100L;

        // Act: the gate shuts from a fully-deployed hold.
        long undelayedStowTick = fullyDeployedTick + UmbrellaCarryAnimations.LOWER_DURATION_TICKS;
        updateThrough(animator, fullyDeployedTick, undelayedStowTick, false);

        // Assert: still up at the tick an undelayed lower would already have finished stowing...
        assertTrue(animator.isVisible(undelayedStowTick, 0.0F),
                "expected the lower to wait out the gate delay before it starts travelling");

        // ...and gone exactly one gate delay later than that.
        long delayedStowTick = undelayedStowTick + GATE_DELAY_TICKS;
        updateThrough(animator, undelayedStowTick, delayedStowTick, false);
        assertFalse(animator.isVisible(delayedStowTick, 0.0F),
                "expected the lower to finish a full gate delay behind an undelayed one");
    }

    @Test
    void update_abandonsAGateChangeWhenTheGateReturnsBeforeTheDelayElapses() {
        // Arrange: a gate that opens and shuts again well inside the delay window -- the flicker the
        // delay exists to swallow.
        UmbrellaAnimator animator = new UmbrellaAnimator(GATE_DELAY_TICKS);
        long blipEndTick = GATE_DELAY_TICKS / 2;
        updateThrough(animator, 0L, blipEndTick - 1, true);

        // Act
        updateThrough(animator, blipEndTick, GATE_DELAY_TICKS, false);

        // Assert: the umbrella never appears, including on the tick the abandoned change would have
        // committed had it survived.
        assertFalse(animator.isVisible(GATE_DELAY_TICKS, 0.0F),
                "expected a gate blip shorter than the delay to leave the umbrella stowed");
    }

    @Test
    void update_restartsTheGateDelayAfterAnAbandonedChange() {
        // Arrange: burn all but one tick of a delay window, then abandon it. A partly spent window that
        // survived would let a gate flickering on this cadence eventually commit on a very short run.
        UmbrellaAnimator animator = new UmbrellaAnimator(GATE_DELAY_TICKS);
        long abandonTick = GATE_DELAY_TICKS - 1;
        updateThrough(animator, 0L, abandonTick - 1, true);
        animator.update(abandonTick, false);

        // Act: the gate opens again on the very next tick.
        long reopenTick = abandonTick + 1;
        long recommitTick = reopenTick + GATE_DELAY_TICKS;
        updateThrough(animator, reopenTick, recommitTick - 1, true);

        // Assert: it waited out a whole fresh window rather than finishing the leftover one.
        assertFalse(animator.isVisible(recommitTick - 1, 0.0F),
                "expected the abandoned window to be discarded rather than resumed");
        animator.update(recommitTick, true);
        assertTrue(animator.isVisible(recommitTick, 0.0F),
                "expected the reopened gate to commit a full delay after it reopened");
    }

    /**
     * Feeds one update per tick over the inclusive range, as a render pass observing every frame
     * does. A state machine driven by a delay only sees the tick it is handed, so a test that skips
     * ticks tests a cadence the renderer never produces.
     */
    private static void updateThrough(UmbrellaAnimator animator,
                                      long fromTick,
                                      long toTick,
                                      boolean shouldDeploy) {
        for (long tick = fromTick; tick <= toTick; tick++) {
            animator.update(tick, shouldDeploy);
        }
    }

    private static float carryArmPitch(UmbrellaAnimator animator, long tick) {
        return carryArmPitch(animator, tick, 0.0F);
    }

    private static float carryArmPitch(UmbrellaAnimator animator, long tick, float partialTicks) {
        AnimationContribution carry = new AnimationContribution(VillagerAnimationRig.RIG);
        animator.contributeCarry(carry, tick, partialTicks);
        return carry.value(ARMS_CROSSED, 0);
    }

    private static float canopyEastRoll(UmbrellaAnimator animator, long tick) {
        return canopyEastRoll(animator, tick, 0.0F);
    }

    private static float canopyEastRoll(UmbrellaAnimator animator, long tick, float partialTicks) {
        AnimationPose canopy = new AnimationPose(UmbrellaAnimationTargets.RIG);
        animator.sampleCanopyInto(canopy, tick, partialTicks);
        return canopy.get(EAST, 2);
    }

    private static float clipArmPitch(KeyframeAnimation carryClip, float elapsedTicks) {
        AnimationContribution carry = new AnimationContribution(VillagerAnimationRig.RIG);
        BakedAnimation.bake(carryClip, VillagerAnimationRig.RIG).contribute(carry, elapsedTicks);
        return carry.value(ARMS_CROSSED, 0);
    }

    private static float clipEastRoll(KeyframeAnimation canopyClip, float elapsedTicks) {
        AnimationPose canopy = new AnimationPose(UmbrellaAnimationTargets.RIG);
        BakedAnimation.bake(canopyClip, UmbrellaAnimationTargets.RIG).sampleInto(canopy, elapsedTicks);
        return canopy.get(EAST, 2);
    }

}
