package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies head and nose continuity across live samples, previews, and resets.
 */
class VillagerAnimationPipelineHeadMotionTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final long RANDOM_SEED = 7L;
    private static final long AGE_TICKS = 1_000L;
    private static final float LOOK_YAW_DEGREES = 30.0F;
    private static final float OTHER_LOOK_YAW_DEGREES = -45.0F;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int HEAD = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
    private static final int NOSE = RIG.indexOf(AnimationTargets.NOSE_ROTATION);

    // A fixed head makes any nose motion caused by the observed look rather than the drawn pose detectable
    private static final KeyframeAnimation HEAD_HOLD = KeyframeAnimation.fromTracks()
            .id(ResourceLocationUtil.mod("animation/test/head_hold"))
            .durationTicks(20)
            .loopMode(LoopMode.LOOP)
            .owns(VillagerAnimationRig.HEAD_ROTATION)
            .track(AnimationTrack.<Vector3f>builder()
                    .target(AnimationTargets.HEAD_ROTATION)
                    .keyframes(List.of(new Keyframe<>(0, new Vector3f(), Easing.LINEAR)))
                    .build())
            .build();

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.GENERIC), HEAD_HOLD,
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SleepingAnimations.sleeping()))
            .build();

    private static final IdleAnimationLibrary IDLE_LIBRARY = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of())
            .build();

    private static final AmbientAnimationLibrary AMBIENT_LIBRARY = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(AmbientAnimations.breathingCycle())
            .blink(AmbientAnimations.blink())
            .browMotions(List.of())
            .build();

    private static final LocomotionAnimationLibrary LOCOMOTION_LIBRARY = InMemoryLocomotionAnimationLibrary.builder()
            .standingPose(LocomotionAnimations.standingPose())
            .animations(Map.of(
                    NavigationType.STROLL, LocomotionAnimations.stroll(),
                    NavigationType.WALK, LocomotionAnimations.walk(),
                    NavigationType.RUN, LocomotionAnimations.jog(),
                    NavigationType.SPRINT, LocomotionAnimations.run(),
                    NavigationType.PANIC, LocomotionAnimations.panicRun()))
            .build();

    private static final BakedAnimationRegistry REGISTRY =
            BakedAnimationRegistry.forVillager(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION_LIBRARY);

    @Test
    void advance_trailsTheHeadTowardANewLook_whileAnObservationalEvaluationSettlesOnIt() {
        // Arrange: a villager turning its look, and one first seen with the new look
        VillagerAnimationPipeline turning = pipeline();
        VillagerAnimationPipeline firstSeen = pipeline();
        VillagerAnimationSnapshot liveSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot settledSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot observationalSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationObservation turned = looking(AGE_TICKS + 1, OTHER_LOOK_YAW_DEGREES).build();
        turning.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), new VillagerAnimationSnapshot());
        turning.advance(turned, liveSnapshot);
        firstSeen.advance(turned, settledSnapshot);

        // Act
        turning.evaluateObservationally(turned, observationalSnapshot);

        // Assert: the live head is mid-turn with its nose trailing; the observational one is settled as after a reset
        float liveYaw = liveSnapshot.getPose().get(HEAD, Y);
        assertTrue(liveYaw < LOOK_YAW_DEGREES * DEGREES_TO_RADIANS - TOLERANCE, "the live head held the old look");
        assertTrue(liveYaw > OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS + TOLERANCE, "the live head jumped");
        assertTrue(Math.abs(liveSnapshot.getPose().get(NOSE, Z)) > TOLERANCE, "the live nose did not trail the turn");
        assertEquals(OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, observationalSnapshot.getPose().get(HEAD, Y),
                TOLERANCE);
        assertEquals(settledSnapshot.getPose().get(HEAD, Y), observationalSnapshot.getPose().get(HEAD, Y), TOLERANCE);
        assertEquals(settledSnapshot.getPose().get(NOSE, Z), observationalSnapshot.getPose().get(NOSE, Z), TOLERANCE);
    }

    @Test
    void evaluateObservationally_midTurn_leavesTheLiveTrailAndNoseUntouched() {
        // Arrange: two villagers mid-turn; the subject is also previewed looking elsewhere, turned, and upside down
        VillagerAnimationPipeline subject = pipeline();
        VillagerAnimationPipeline control = pipeline();
        for (VillagerAnimationPipeline pipeline : List.of(subject, control)) {
            pipeline.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), new VillagerAnimationSnapshot());
            pipeline.advance(looking(AGE_TICKS + 1, OTHER_LOOK_YAW_DEGREES).build(), new VillagerAnimationSnapshot());
        }
        VillagerAnimationObservation preview = looking(AGE_TICKS + 1, 80.0F)
                .partialTicks(1.0F)
                .bodyHeadingDegrees(90.0F)
                .upsideDown(true)
                .build();
        VillagerAnimationObservation next = looking(AGE_TICKS + 2, OTHER_LOOK_YAW_DEGREES).build();
        VillagerAnimationSnapshot subjectNext = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlNext = new VillagerAnimationSnapshot();

        // Act
        subject.evaluateObservationally(preview, new VillagerAnimationSnapshot());
        subject.advance(next, subjectNext);
        control.advance(next, controlNext);

        // Assert: a preview that settled the live followers would leave the subject's head and nose elsewhere
        assertTrue(Math.abs(controlNext.getPose().get(NOSE, Z)) > TOLERANCE, "the control's nose did not trail");
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(controlNext.getPose().get(HEAD, axis), subjectNext.getPose().get(HEAD, axis), 0.0F);
            assertEquals(controlNext.getPose().get(NOSE, axis), subjectNext.getPose().get(NOSE, axis), 0.0F);
        }
    }

    @Test
    void waking_settlesTheHeadOnTheLook_atOnce() {
        // Arrange: one villager sleeps through three ticks, the control stays awake; both look aside throughout
        VillagerAnimationPipeline sleeper = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot sleeperSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        sleeper.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), sleeperSnapshot);
        control.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), controlSnapshot);
        for (long age = AGE_TICKS + 1; age <= AGE_TICKS + 3; age++) {
            sleeper.advance(looking(age, LOOK_YAW_DEGREES).sleeping(true).build(), sleeperSnapshot);
            control.advance(looking(age, LOOK_YAW_DEGREES).build(), controlSnapshot);
        }
        VillagerAnimationObservation woken = looking(AGE_TICKS + 4, OTHER_LOOK_YAW_DEGREES).build();

        // Act
        sleeper.advance(woken, sleeperSnapshot);
        control.advance(woken, controlSnapshot);

        // Assert: without the reset on waking, the head would turn from where it was before sleep, as the control's
        assertEquals(OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, sleeperSnapshot.getPose().get(HEAD, Y), TOLERANCE);
        assertTrue(controlSnapshot.getPose().get(HEAD, Y) > OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS + TOLERANCE,
                "the control's head jumped");
    }

    @Test
    void forwardJump_pastTheResetInterval_settlesTheHeadOnTheLook_andOneWithinItTrails() {
        // Arrange
        VillagerAnimationPipeline returning = pipeline();
        VillagerAnimationPipeline hitching = pipeline();
        VillagerAnimationSnapshot returningSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot hitchingSnapshot = new VillagerAnimationSnapshot();
        returning.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), returningSnapshot);
        hitching.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), hitchingSnapshot);
        long resetInterval = (long) FollowerStep.RESET_INTERVAL_TICKS;

        // Act
        returning.advance(looking(AGE_TICKS + resetInterval + 1, OTHER_LOOK_YAW_DEGREES).build(), returningSnapshot);
        hitching.advance(looking(AGE_TICKS + resetInterval, OTHER_LOOK_YAW_DEGREES).build(), hitchingSnapshot);

        // Assert: a gap past the reset interval snaps to the current look; a shorter hitch preserves the trail
        assertEquals(OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, returningSnapshot.getPose().get(HEAD, Y),
                TOLERANCE);
        assertTrue(hitchingSnapshot.getPose().get(HEAD, Y) > OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS + TOLERANCE,
                "a hitch within the reset interval jumped the head");
    }

    @Test
    void sleep_suppressesTheNoseLag_whileTheBodyTurns() {
        // Arrange: the same body turn under a sleeping villager and an awake one
        VillagerAnimationPipeline sleeping = pipeline();
        VillagerAnimationPipeline awake = pipeline();
        VillagerAnimationSnapshot sleepingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot awakeSnapshot = new VillagerAnimationSnapshot();
        AnimationPose sleepClip = new AnimationPose(RIG);
        BakedAnimation bakedSleep = BakedAnimation.bake(SleepingAnimations.sleeping(), RIG);

        for (int tick = 0; tick <= 5; tick++) {
            float bodyHeading = 10.0F * tick;

            // Act
            sleeping.advance(looking(AGE_TICKS + tick, 0.0F).bodyHeadingDegrees(bodyHeading).sleeping(true).build(),
                    sleepingSnapshot);
            awake.advance(looking(AGE_TICKS + tick, 0.0F).bodyHeadingDegrees(bodyHeading).build(), awakeSnapshot);

            // Assert: the exclusive sleep pose suppresses the lag, so the nose rolls only as the clip does
            bakedSleep.sampleInto(sleepClip, tick);
            assertEquals(sleepClip.get(NOSE, Z), sleepingSnapshot.getPose().get(NOSE, Z), TOLERANCE, "tick " + tick);
        }
        assertTrue(Math.abs(awakeSnapshot.getPose().get(NOSE, Z)) > TOLERANCE, "the turn never rolled an awake nose");
    }

    @Test
    void noseLag_followsTheDrawnHead_notTheObservedLook() {
        // Arrange: a held head over a swinging look, against a held head over a still look
        VillagerAnimationPipeline swingingLook = pipeline();
        VillagerAnimationPipeline stillLook = pipeline();
        VillagerAnimationSnapshot swingingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot stillSnapshot = new VillagerAnimationSnapshot();

        for (int tick = 0; tick <= 5; tick++) {
            float swingingYaw = tick % 2 == 0 ? LOOK_YAW_DEGREES : OTHER_LOOK_YAW_DEGREES;

            // Act
            swingingLook.advance(holdingTheHead(AGE_TICKS + tick, swingingYaw), swingingSnapshot);
            stillLook.advance(holdingTheHead(AGE_TICKS + tick, LOOK_YAW_DEGREES), stillSnapshot);

            // Assert: a nose following the observed look would roll with the swing the held head never makes
            assertEquals(stillSnapshot.getPose().get(NOSE, Z), swingingSnapshot.getPose().get(NOSE, Z), TOLERANCE,
                    "tick " + tick);
        }
        assertEquals(0.0F, swingingSnapshot.getPose().get(HEAD, Y), TOLERANCE);
    }

    private static VillagerAnimationPipeline pipeline() {
        return new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION_LIBRARY,
                REGISTRY, new UmbrellaAnimator(0), new Random(RANDOM_SEED));
    }

    /**
     * Returns an observation requesting the head-hold action at the given age and look yaw.
     */
    private static VillagerAnimationObservation holdingTheHead(long age, float netHeadYawDegrees) {
        return looking(age, netHeadYawDegrees).motionArchetype(AnimationArchetype.INTERACT).build();
    }

    /**
     * An awake, contented villager standing still with no action, looking level by this yaw from a body facing zero.
     */
    private static VillagerAnimationObservations.ObservationBuilder looking(long age, float netHeadYawDegrees) {
        return VillagerAnimationObservations.observation()
                .ageTicks(age)
                .netHeadYawDegrees(netHeadYawDegrees)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic());
    }

}
