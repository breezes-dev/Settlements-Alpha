package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies gaze through the pipeline: its sources over a turn and a head hold, suppression, containment, and
 * observational evaluation.
 */
class VillagerAnimationPipelineGazeTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final int X = 0;
    private static final int Y = 1;

    private static final long RANDOM_SEED = 7L;
    private static final long AGE_TICKS = 1_000L;

    // A small look, so no source's share of it comes near a pupil bound
    private static final float LOOK_YAW_DEGREES = 15.0F;
    // A far look down and to the villager's left, which every source pushes past the pupil bounds the hold keys
    private static final float FAR_LOOK_DEGREES = 40.0F;

    // Far longer than the head takes to reach a look that holds still
    private static final long SETTLING_TICKS = 3L * ClockTicks.TICKS_PER_SECOND;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int LEFT_PUPIL = RIG.indexOf(AnimationTargets.PUPIL_LEFT_TRANSLATION);
    private static final int RIGHT_PUPIL = RIG.indexOf(AnimationTargets.PUPIL_RIGHT_TRANSLATION);
    private static final int LEFT_PUPIL_SCALE = RIG.indexOf(AnimationTargets.PUPIL_LEFT_SCALE);
    private static final int RIGHT_PUPIL_SCALE = RIG.indexOf(AnimationTargets.PUPIL_RIGHT_SCALE);

    // Holding the head straight keeps a look residual for any look aside
    private static final KeyframeAnimation HEAD_HOLD = headHold("head_hold").build();
    private static final KeyframeAnimation GAZE_SUPPRESSING_HEAD_HOLD = headHold("gaze_suppressing_head_hold")
            .suppresses(SuppressibleComponent.GAZE)
            .build();
    // Also keys the left pupil against the white's outer and bottom edges, so any gaze outward or down presses past
    private static final KeyframeAnimation HEAD_HOLD_AT_PUPIL_BOUNDS = headHold("head_hold_at_pupil_bounds")
            .track(AnimationTrack.<Vec3>builder()
                    .target(AnimationTargets.PUPIL_LEFT_TRANSLATION)
                    .keyframes(List.of(new Keyframe<>(0, new Vec3(
                            VillagerEye.WHITE_HALF_WIDTH - VillagerEye.PUPIL_REST_SIZE / 2.0F
                                    - VillagerEye.LEFT.pupilRestX(),
                            VillagerEye.WHITE_HALF_HEIGHT - VillagerEye.PUPIL_REST_SIZE / 2.0F
                                    - VillagerEye.PUPIL_REST_Y,
                            0.0), Easing.LINEAR)))
                    .build())
            .build();

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.THROW, ItemCategory.GENERIC), HEAD_HOLD,
                    AnimationKey.of(AnimationArchetype.EAT, ItemCategory.GENERIC), GAZE_SUPPRESSING_HEAD_HOLD,
                    AnimationKey.of(AnimationArchetype.REEL_IN, ItemCategory.GENERIC), HEAD_HOLD_AT_PUPIL_BOUNDS,
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SleepingAnimations.sleeping()))
            .build();

    private static final IdleAnimationLibrary IDLE_LIBRARY = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of())
            .build();

    // Breathing turns the torso, which would carry the drawn head off the look and move the pupils with it
    private static final KeyframeAnimation STILL_BREATHING = KeyframeAnimation.fromTracks()
            .id(ResourceLocationUtil.mod("animation/test/still_breathing"))
            .durationTicks(AmbientAnimations.BREATHING_CYCLE_DURATION_TICKS)
            .loopMode(LoopMode.LOOP)
            .build();

    private static final AmbientAnimationLibrary AMBIENT_LIBRARY = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(STILL_BREATHING)
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
    void eyes_leadATurnOfTheLook_thenRecenterAsTheHeadArrives_whileAnObservationalEvaluationIsSettled() {
        // Arrange: a villager whose look turns right, and one first seen with the new look
        VillagerAnimationPipeline turning = pipeline();
        VillagerAnimationPipeline firstSeen = pipeline();
        VillagerAnimationSnapshot turningSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot settledSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot observationalSnapshot = new VillagerAnimationSnapshot();
        turning.advance(looking(AGE_TICKS, 0.0F).build(), turningSnapshot);
        VillagerAnimationObservation turned = looking(AGE_TICKS + 1, LOOK_YAW_DEGREES).build();
        firstSeen.advance(turned, settledSnapshot);

        // Act
        turning.advance(turned, turningSnapshot);
        turning.evaluateObservationally(turned, observationalSnapshot);

        // Assert: the right pupil moves outward toward the look; led by the residual it starts past where the head's
        // turn alone settles it, and an observational evaluation shows the settled eyes
        float settledX = rightPupilX(settledSnapshot);
        float previousX = rightPupilX(turningSnapshot);
        assertTrue(settledX < -TOLERANCE, "the settled eyes did not look further right: " + settledX);
        assertTrue(previousX < settledX - TOLERANCE, "the eyes did not lead the head: " + previousX);
        assertEquals(settledX, rightPupilX(observationalSnapshot), TOLERANCE);
        for (long age = AGE_TICKS + 2; age <= AGE_TICKS + SETTLING_TICKS; age++) {
            turning.advance(looking(age, LOOK_YAW_DEGREES).build(), turningSnapshot);
            float x = rightPupilX(turningSnapshot);
            assertTrue(x >= previousX - TOLERANCE, "the eyes moved away from center at age " + age);
            previousX = x;
        }
        assertEquals(settledX, previousX, TOLERANCE, "the eyes did not recenter as the head arrived");
    }

    @Test
    void authoredHeadHold_leavesTheEyesCarryingTheLook() {
        // Arrange: the same look to the right, with the head held straight ahead and with the head free to follow
        VillagerAnimationPipeline holding = pipeline();
        VillagerAnimationPipeline following = pipeline();
        VillagerAnimationSnapshot holdingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot followingSnapshot = new VillagerAnimationSnapshot();

        // Act
        holding.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).motionArchetype(AnimationArchetype.THROW).build(),
                holdingSnapshot);
        following.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).build(), followingSnapshot);

        // Assert: a head frame missing the hold would see the held head drawn on the look, as the free one is, and give
        // both villagers the same eyes
        float followingX = rightPupilX(followingSnapshot);
        float holdingX = rightPupilX(holdingSnapshot);
        assertTrue(followingX < -TOLERANCE, "the free head's eyes did not look further right");
        assertTrue(holdingX < -TOLERANCE, "the held head's eyes did not carry the look");
        assertTrue(Math.abs(holdingX - followingX) > TOLERANCE, "the hold left the eyes where the free head's are");
    }

    @Test
    void gazeSuppression_hidesTheEyesCarryingTheLook() {
        // Arrange: both villagers hold the head straight while looking aside; one hold suppresses gaze
        VillagerAnimationPipeline suppressed = pipeline();
        VillagerAnimationPipeline control = pipeline();
        VillagerAnimationSnapshot suppressedSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();

        // Act
        suppressed.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).headPitchDegrees(LOOK_YAW_DEGREES)
                .motionArchetype(AnimationArchetype.EAT).build(), suppressedSnapshot);
        control.advance(looking(AGE_TICKS, LOOK_YAW_DEGREES).headPitchDegrees(LOOK_YAW_DEGREES)
                .motionArchetype(AnimationArchetype.THROW).build(), controlSnapshot);

        // Assert: a gaze ignoring its remaining weight would move the suppressed pupils as the control's move
        assertTrue(pupilsOffRest(controlSnapshot), "the control's eyes did not carry the look");
        assertFalse(pupilsOffRest(suppressedSnapshot), "suppressed eyes moved");
    }

    @Test
    void containment_boundsTheGaze_andSleepLeavesNoGazeToBound() {
        // Arrange: a villager holding its left pupil against the white's outer and bottom edges while looking far
        // that way, and two asleep, one looking the same way and one straight ahead
        VillagerAnimationPipeline awake = pipeline();
        VillagerAnimationPipeline asleepLookingAside = pipeline();
        VillagerAnimationPipeline asleepLookingAhead = pipeline();
        VillagerAnimationSnapshot awakeSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot asleepAsideSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot asleepAheadSnapshot = new VillagerAnimationSnapshot();

        // Act
        awake.advance(lookingFarDownLeft(AGE_TICKS).motionArchetype(AnimationArchetype.REEL_IN).build(), awakeSnapshot);
        asleepLookingAside.advance(lookingFarDownLeft(AGE_TICKS).sleeping(true).build(), asleepAsideSnapshot);
        asleepLookingAhead.advance(looking(AGE_TICKS, 0.0F).sleeping(true).build(), asleepAheadSnapshot);

        // Assert: gaze applied after the containment would carry the pupils past the white's edges; gaze not reaching
        // them would leave the left one unsquashed and the right one at rest
        PupilEdges edges = PupilEdges.of(awakeSnapshot.getPose(), VillagerEye.LEFT);
        assertEquals(VillagerEye.WHITE_HALF_WIDTH, edges.maxX(), TOLERANCE);
        assertEquals(VillagerEye.WHITE_HALF_HEIGHT, edges.bottomY(), TOLERANCE);
        assertTrue(edges.height() < VillagerEye.PUPIL_REST_SIZE - TOLERANCE, "the gaze never pressed the pupil down");
        // The same look carries the right pupil inward, where it squashes against its eye's inner edge
        PupilEdges inward = PupilEdges.of(awakeSnapshot.getPose(), VillagerEye.RIGHT);
        assertEquals(VillagerEye.RIGHT.innerEdgeX(), inward.maxX(), TOLERANCE);
        assertTrue(inward.maxX() - inward.minX() < VillagerEye.PUPIL_REST_SIZE - TOLERANCE,
                "the inward pupil was not squashed against its inner edge");
        // Sleep suppresses gaze, so where the sleeper looks changes nothing about its pupils
        for (int channel : new int[]{LEFT_PUPIL, RIGHT_PUPIL, LEFT_PUPIL_SCALE, RIGHT_PUPIL_SCALE}) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                assertEquals(asleepAheadSnapshot.getPose().get(channel, axis),
                        asleepAsideSnapshot.getPose().get(channel, axis), 0.0F);
            }
        }
    }

    private static VillagerAnimationPipeline pipeline() {
        return new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION_LIBRARY,
                REGISTRY, new UmbrellaAnimator(0), new Random(RANDOM_SEED));
    }

    private static TrackAnimationBuilder headHold(String name) {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/" + name))
                .durationTicks(20)
                .loopMode(LoopMode.LOOP)
                .owns(VillagerAnimationRig.HEAD_ROTATION)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.HEAD_ROTATION)
                        .keyframes(List.of(new Keyframe<>(0, new Vector3f(), Easing.LINEAR)))
                        .build());
    }

    private static float rightPupilX(VillagerAnimationSnapshot snapshot) {
        return snapshot.getPose().get(RIGHT_PUPIL, X);
    }

    private static boolean pupilsOffRest(VillagerAnimationSnapshot snapshot) {
        AnimationPose pose = snapshot.getPose();
        return pose.get(LEFT_PUPIL, X) != 0.0F || pose.get(LEFT_PUPIL, Y) != 0.0F
                || pose.get(RIGHT_PUPIL, X) != 0.0F || pose.get(RIGHT_PUPIL, Y) != 0.0F;
    }

    private static VillagerAnimationObservations.ObservationBuilder lookingFarDownLeft(long age) {
        // Negative yaw turns toward the villager's left, and positive pitch looks down
        return looking(age, -FAR_LOOK_DEGREES).headPitchDegrees(FAR_LOOK_DEGREES);
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
