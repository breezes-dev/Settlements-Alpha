package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerAnimationPipelineLanternTest {

    private static final long FIRST_TICK = 1_000L;
    private static final long RANDOM_SEED = 19L;
    private static final float TOLERANCE = 1.0E-5F;
    private static final int CROSSED_ARMS = VillagerAnimationRig.RIG.indexOf(AnimationTargets.ARMS_CROSSED_ROTATION);
    private static final int CLAIM_DURATION_TICKS = 80;
    private static final int CLAIM_BLEND_TICKS = 6;
    private static final int TIMED_CLAIM_TICK = 7;

    private static final KeyframeAnimation LEFT_STRAIGHT =
            claiming("left_straight", ArmConfiguration.RIGHT_CROSSED_LEFT_STRAIGHT);
    private static final KeyframeAnimation RIGHT_STRAIGHT =
            claiming("right_straight", ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT);
    private static final KeyframeAnimation CROSSED = claiming("crossed", ArmConfiguration.BOTH_CROSSED);
    private static final KeyframeAnimation TIMED_CLAIMS = KeyframeAnimation.fromTracks()
            .id(ResourceLocationUtil.mod("animation/test/lantern_timed_claims"))
            .durationTicks(CLAIM_DURATION_TICKS)
            .loopMode(LoopMode.LOOP)
            .blendInTicks(CLAIM_BLEND_TICKS)
            .arms(ArmConfiguration.BOTH_CROSSED)
            .armConfigurationAt(TIMED_CLAIM_TICK, ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT)
            .build();

    private static final AnimationLibrary ACTIONS = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), LEFT_STRAIGHT,
                    AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.GENERIC), RIGHT_STRAIGHT,
                    AnimationKey.of(AnimationArchetype.POINT, ItemCategory.GENERIC), CROSSED,
                    AnimationKey.of(AnimationArchetype.HARVEST, ItemCategory.GENERIC), TIMED_CLAIMS,
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SleepingAnimations.sleeping()))
            .build();

    private static final IdleAnimationLibrary IDLE = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of())
            .build();
    private static final AmbientAnimationLibrary AMBIENT = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(AmbientAnimations.breathingCycle())
            .blink(AmbientAnimations.blink())
            .browMotions(List.of())
            .build();

    private enum Yield {
        LEFT_STRAIGHT_ARM,
        RIGHT_STRAIGHT_ARM,
        HAND_ITEM,
        SLEEP,
        UMBRELLA
    }

    private enum LiveCarry {
        HOLD,
        SHOW,
        STOW
    }

    @ParameterizedTest
    @EnumSource(Yield.class)
    void lantern_hidesOnTheSameSample_thatAClaimOrOtherYieldAppears(Yield yieldCondition) {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        pipeline.advance(observation(FIRST_TICK, true), snapshot);
        assertTrue(snapshot.isLanternVisible());
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration());
        VillagerAnimationObservation yielding = observation(FIRST_TICK + 1, true);
        switch (yieldCondition) {
            case LEFT_STRAIGHT_ARM -> yielding.setMotion(AnimationArchetype.WAVE, (byte) 1);
            case RIGHT_STRAIGHT_ARM -> yielding.setMotion(AnimationArchetype.INTERACT, (byte) 1);
            case HAND_ITEM -> yielding.setHandItemShown(true);
            case SLEEP -> yielding.setSleeping(true);
            case UMBRELLA -> yielding.setUmbrellaRequested(true);
        }

        // Act
        pipeline.advance(yielding, snapshot);

        // Assert: a new action's blend weight is still zero, but its arm claims already apply.
        assertFalse(snapshot.isLanternVisible());
        if (yieldCondition == Yield.LEFT_STRAIGHT_ARM) {
            assertEquals(ArmConfiguration.RIGHT_CROSSED_LEFT_STRAIGHT, snapshot.getArmConfiguration());
        } else if (yieldCondition == Yield.RIGHT_STRAIGHT_ARM) {
            assertEquals(ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT, snapshot.getArmConfiguration());
        } else if (yieldCondition == Yield.UMBRELLA) {
            assertTrue(snapshot.isUmbrellaVisible());
        }
    }

    @Test
    void lantern_hidesAtTheTimedArmClaimBoundary_includingPartialTicks() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        pipeline.advance(observation(FIRST_TICK, true), snapshot);
        long actionStarts = FIRST_TICK + 1;
        long beforeBoundaryTick = actionStarts + TIMED_CLAIM_TICK - 1;
        for (long tick = actionStarts; tick < beforeBoundaryTick; tick++) {
            VillagerAnimationObservation acting = observation(tick, true);
            acting.setMotion(AnimationArchetype.HARVEST, (byte) 1);
            pipeline.advance(acting, snapshot);
        }
        VillagerAnimationObservation before = observation(beforeBoundaryTick, true);
        before.setMotion(AnimationArchetype.HARVEST, (byte) 1);
        before.setSampleKey(beforeBoundaryTick, 0.99F);
        VillagerAnimationObservation boundary = observation(beforeBoundaryTick, true);
        boundary.setMotion(AnimationArchetype.HARVEST, (byte) 1);
        boundary.setSampleKey(beforeBoundaryTick, 1.0F);

        // Act, Assert: sampling claims at whole ticks would leave the prop visible beside a straight arm.
        pipeline.advance(before, snapshot);
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration());
        assertTrue(snapshot.isLanternVisible());
        pipeline.advance(boundary, snapshot);
        assertEquals(ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT, snapshot.getArmConfiguration());
        assertFalse(snapshot.isLanternVisible());
    }

    @Test
    void lantern_yieldsOnTheSample_aScheduledIdleClaimsAStraightArm() {
        // Arrange
        IdleAnimationLibrary idle = InMemoryIdleAnimationLibrary.builder()
                .idleAnimations(List.of(LEFT_STRAIGHT))
                .build();
        VillagerAnimationPipeline pipeline = pipeline(LocomotionAnimations.standingPose(), idle);
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        pipeline.advance(observation(FIRST_TICK, true), snapshot);
        assertTrue(snapshot.isLanternVisible());
        long latestIdleStart = FIRST_TICK + IdleAnimationScheduler.MIN_DELAY_TICKS
                + IdleAnimationScheduler.DELAY_RANGE_TICKS;
        boolean idleStarted = false;

        // Act, Assert: checking claims before advancing the scheduler misses the idle's first sample.
        for (long tick = FIRST_TICK + 1; tick <= latestIdleStart; tick++) {
            pipeline.advance(observation(tick, true), snapshot);
            if (snapshot.getArmConfiguration() == ArmConfiguration.RIGHT_CROSSED_LEFT_STRAIGHT) {
                idleStarted = true;
                assertFalse(snapshot.isLanternVisible());
                break;
            }
            assertTrue(snapshot.isLanternVisible(), "before the idle at " + tick);
        }
        assertTrue(idleStarted, "the idle must start within its delay range");
    }

    @Test
    void lantern_usesTheWinningArmClaims_whenAnActionCrossesStraightStandingArms() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline(claiming("straight_standing", ArmConfiguration.BOTH_STRAIGHT));
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        pipeline.advance(observation(FIRST_TICK, true), snapshot);
        assertFalse(snapshot.isLanternVisible());
        long actionStarts = FIRST_TICK + 1;
        long returnPeak = actionStarts + LanternAnimator.RETURN_SETTLE_TICKS + InteractAnimations.INTERACT_PEAK_TICK;

        // Act
        for (long tick = actionStarts; tick <= returnPeak; tick++) {
            VillagerAnimationObservation crossedAction = observation(tick, true);
            crossedAction.setMotion(AnimationArchetype.POINT, (byte) 1);
            pipeline.advance(crossedAction, snapshot);
        }

        // Assert: a lower straight claim must not keep yielding after a higher crossed claim wins.
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration());
        assertTrue(snapshot.isLanternVisible());
    }

    @Test
    void lantern_waitsThroughTheUmbrellasFullStow_thenReturnsWithTheShowGesture() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationPipeline withoutLantern = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot withoutLanternSnapshot = new VillagerAnimationSnapshot();
        pipeline.advance(observation(FIRST_TICK, true), snapshot);
        withoutLantern.advance(observation(FIRST_TICK, false), withoutLanternSnapshot);
        assertTrue(snapshot.isLanternVisible());
        long rainStarts = FIRST_TICK + 1;
        long rainStops = rainStarts + Math.max(UmbrellaCarryAnimations.RAISE_DURATION_TICKS,
                UmbrellaCarryAnimations.RAISE_CANOPY_START_TICK + UmbrellaAnimations.DEPLOY_DURATION_TICKS) + 1;
        long umbrellaCloses = rainStops + Math.max(UmbrellaCarryAnimations.LOWER_DURATION_TICKS,
                UmbrellaCarryAnimations.LOWER_CANOPY_START_TICK + UmbrellaAnimations.UNDEPLOY_DURATION_TICKS);
        long showStarts = umbrellaCloses + LanternAnimator.RETURN_SETTLE_TICKS;
        long showPeak = showStarts + InteractAnimations.INTERACT_PEAK_TICK;

        // Act, Assert
        for (long tick = rainStarts; tick <= showPeak; tick++) {
            VillagerAnimationObservation current = observation(tick, true);
            current.setUmbrellaRequested(tick < rainStops);
            pipeline.advance(current, snapshot);
            VillagerAnimationObservation control = observation(tick, false);
            control.setUmbrellaRequested(tick < rainStops);
            withoutLantern.advance(control, withoutLanternSnapshot);
            assertEquals(tick < umbrellaCloses, snapshot.isUmbrellaVisible(), "umbrella at " + tick);
            assertEquals(tick >= showPeak, snapshot.isLanternVisible(), "lantern at " + tick);
            if (tick == showStarts) {
                assertEquals(0.0F, crossedArmPitch(snapshot) - crossedArmPitch(withoutLanternSnapshot), TOLERANCE);
            }
        }
        assertEquals((float) Math.toRadians(InteractAnimations.INTERACT_PEAK_PITCH_DEGREES),
                crossedArmPitch(snapshot) - crossedArmPitch(withoutLanternSnapshot), TOLERANCE);
    }

    @Test
    void lantern_firstDecidedObservationHoldsImmediately_afterUndecidedLiveObservations() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationPipeline withoutLantern = pipeline();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot withoutLanternSnapshot = new VillagerAnimationSnapshot();
        for (long tick = FIRST_TICK; tick < FIRST_TICK + 2; tick++) {
            VillagerAnimationObservation undecided = observation(tick, false);
            undecided.setLanternCheckDecided(false);
            pipeline.advance(undecided, snapshot);
            withoutLantern.advance(undecided, withoutLanternSnapshot);
            assertFalse(snapshot.isLanternVisible());
            assertEquals(0.0F, crossedArmPitch(snapshot) - crossedArmPitch(withoutLanternSnapshot), TOLERANCE);
        }

        // Act
        pipeline.advance(observation(FIRST_TICK + 2, true), snapshot);
        withoutLantern.advance(observation(FIRST_TICK + 2, false), withoutLanternSnapshot);

        // Assert
        assertTrue(snapshot.isLanternVisible());
        assertEquals((float) Math.toRadians(LanternCarryAnimations.HOLD_PITCH_DEGREES),
                crossedArmPitch(snapshot) - crossedArmPitch(withoutLanternSnapshot), TOLERANCE);
    }

    @Test
    void lantern_observationalEvaluationCannotConsumeFirstSight() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationPipeline withoutLantern = pipeline();
        VillagerAnimationSnapshot preview = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot live = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot withoutLanternSnapshot = new VillagerAnimationSnapshot();

        // Act
        pipeline.evaluateObservationally(observation(FIRST_TICK + CLAIM_DURATION_TICKS, false), preview);
        pipeline.advance(observation(FIRST_TICK, true), live);
        withoutLantern.advance(observation(FIRST_TICK, false), withoutLanternSnapshot);

        // Assert
        assertFalse(preview.isLanternVisible());
        assertTrue(live.isLanternVisible());
        assertEquals((float) Math.toRadians(LanternCarryAnimations.HOLD_PITCH_DEGREES),
                crossedArmPitch(live) - crossedArmPitch(withoutLanternSnapshot), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(LiveCarry.class)
    void lantern_observationalEvaluationPreservesLiveHoldAndGestures(LiveCarry state) {
        // Arrange
        VillagerAnimationPipeline previewed = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot previewedSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        boolean initiallyRequested = state != LiveCarry.SHOW;
        boolean requestedAfterChange = state != LiveCarry.STOW;
        previewed.advance(observation(FIRST_TICK, initiallyRequested), previewedSnapshot);
        control.advance(observation(FIRST_TICK, initiallyRequested), controlSnapshot);
        long changeTick = FIRST_TICK + 1;
        long previewTick = changeTick + InteractAnimations.INTERACT_PEAK_TICK / 2;
        for (long tick = changeTick; tick <= previewTick; tick++) {
            previewed.advance(observation(tick, requestedAfterChange), previewedSnapshot);
            control.advance(observation(tick, requestedAfterChange), controlSnapshot);
        }
        VillagerAnimationObservation previewObservation =
                observation(previewTick + CLAIM_DURATION_TICKS, !requestedAfterChange);
        previewObservation.setSleeping(true);
        previewObservation.setHandItemShown(true);
        previewObservation.setUmbrellaRequested(true);
        long gestureEnds = changeTick + InteractAnimations.INTERACT_DURATION_TICKS;

        // Act
        previewed.evaluateObservationally(previewObservation, new VillagerAnimationSnapshot());

        // Assert: a preview must neither commit its gate nor interrupt a live carry gesture.
        for (long tick = previewTick + 1; tick <= gestureEnds + LanternAnimator.RETURN_SETTLE_TICKS; tick++) {
            previewed.advance(observation(tick, requestedAfterChange), previewedSnapshot);
            control.advance(observation(tick, requestedAfterChange), controlSnapshot);
            assertEquals(controlSnapshot.isLanternVisible(), previewedSnapshot.isLanternVisible(), "at " + tick);
            assertEquals(crossedArmPitch(controlSnapshot), crossedArmPitch(previewedSnapshot), TOLERANCE,
                    "at " + tick);
        }
        assertEquals(requestedAfterChange, previewedSnapshot.isLanternVisible());
    }

    private static VillagerAnimationPipeline pipeline() {
        return pipeline(LocomotionAnimations.standingPose());
    }

    private static VillagerAnimationPipeline pipeline(KeyframeAnimation standingPose) {
        return pipeline(standingPose, IDLE);
    }

    private static VillagerAnimationPipeline pipeline(KeyframeAnimation standingPose, IdleAnimationLibrary idle) {
        LocomotionAnimationLibrary locomotion = InMemoryLocomotionAnimationLibrary.builder()
                .standingPose(standingPose)
                .animations(Map.of(
                        NavigationType.STROLL, LocomotionAnimations.stroll(),
                        NavigationType.WALK, LocomotionAnimations.walk(),
                        NavigationType.RUN, LocomotionAnimations.run(),
                        NavigationType.SPRINT, LocomotionAnimations.sprint()))
                .build();
        BakedAnimationRegistry registry = BakedAnimationRegistry.forVillager(ACTIONS, idle, AMBIENT, locomotion);
        return new VillagerAnimationPipeline(ACTIONS, idle, AMBIENT, locomotion, registry,
                new UmbrellaAnimator(0), new LanternAnimator(0), new Random(RANDOM_SEED));
    }

    private static VillagerAnimationObservation observation(long tick, boolean requested) {
        return VillagerAnimationObservations.observation()
                .ageTicks(tick)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic())
                .lanternCheckDecided(true)
                .lanternRequested(requested)
                .build();
    }

    private static KeyframeAnimation claiming(String name, ArmConfiguration arms) {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/lantern_" + name))
                .durationTicks(CLAIM_DURATION_TICKS)
                .loopMode(LoopMode.LOOP)
                .blendInTicks(CLAIM_BLEND_TICKS)
                .arms(arms)
                .build();
    }

    private static float crossedArmPitch(VillagerAnimationSnapshot snapshot) {
        return snapshot.getPose().get(CROSSED_ARMS, 0);
    }

}
