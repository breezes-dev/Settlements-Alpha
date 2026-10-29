package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ItemCategory;
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
 * Verifies the ambient brow and the brow reaction through the pipeline: suppression, observational settling, and the
 * upside-down mirror.
 */
class VillagerAnimationPipelineBrowTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final long RANDOM_SEED = 7L;
    private static final long AGE_TICKS = 1_000L;
    private static final float LOOK_YAW_DEGREES = 30.0F;
    private static final float WIDE_LOOK_YAW_DEGREES = 40.0F;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int BROW_TRANSLATION = RIG.indexOf(AnimationTargets.MONOBROW_TRANSLATION);
    private static final int BROW_ROTATION = RIG.indexOf(AnimationTargets.MONOBROW_ROTATION);

    private static final int SUPPRESSION_TICKS = 20;

    // Holding the head straight keeps a residual for any look aside
    private static final KeyframeAnimation HEAD_HOLD = headHold("head_hold").build();
    private static final KeyframeAnimation BROW_SUPPRESSING_HEAD_HOLD = headHold("brow_suppressing_head_hold")
            .suppresses(SuppressibleComponent.BROW)
            .build();
    // An authored raise between the procedural raise limit and the final bound
    private static final float AUTHORED_RAISE =
            (BrowReaction.PROCEDURAL_RAISE_LIMIT + BrowReaction.RAISED_OFFSET_LIMIT) / 2.0F;
    private static final KeyframeAnimation RAISED_BROW_HEAD_HOLD = headHold("raised_brow_head_hold")
            .track(AnimationTrack.<Vec3>builder()
                    .target(AnimationTargets.MONOBROW_TRANSLATION)
                    .keyframes(List.of(new Keyframe<>(0, new Vec3(0.0, AUTHORED_RAISE, 0.0), Easing.LINEAR)))
                    .build())
            .build();
    // Keys and owns nothing, so it changes the brow's suppression and nothing else
    private static final KeyframeAnimation BROW_SUPPRESSION = KeyframeAnimation.fromTracks()
            .id(ResourceLocationUtil.mod("animation/test/brow_suppression"))
            .durationTicks(SUPPRESSION_TICKS)
            .suppresses(SuppressibleComponent.BROW)
            .build();

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.GENERIC), HEAD_HOLD,
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), BROW_SUPPRESSING_HEAD_HOLD,
                    AnimationKey.of(AnimationArchetype.THROW, ItemCategory.GENERIC), BROW_SUPPRESSION,
                    AnimationKey.of(AnimationArchetype.EAT, ItemCategory.GENERIC), RAISED_BROW_HEAD_HOLD,
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SleepingAnimations.sleeping()))
            .build();

    private static final IdleAnimationLibrary IDLE_LIBRARY = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of())
            .build();

    // Breathing turns the torso, which would carry the drawn head off the look and make the brow react to it
    private static final KeyframeAnimation STILL_BREATHING = KeyframeAnimation.fromTracks()
            .id(ResourceLocationUtil.mod("animation/test/still_breathing"))
            .durationTicks(AmbientAnimations.BREATHING_CYCLE_DURATION_TICKS)
            .loopMode(LoopMode.LOOP)
            .build();

    private static final AmbientAnimationLibrary WITH_BROW_MOTIONS = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(STILL_BREATHING)
            .blink(AmbientAnimations.blink())
            .browMotions(List.of(
                    AmbientAnimations.browRaise(),
                    AmbientAnimations.browRaiseLeftEnd(),
                    AmbientAnimations.browRaiseRightEnd()))
            .build();

    // Only the raise, so every ambient brow motion that comes due lifts the brow
    private static final AmbientAnimationLibrary WITH_ONLY_THE_BROW_RAISE = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(STILL_BREATHING)
            .blink(AmbientAnimations.blink())
            .browMotions(List.of(AmbientAnimations.browRaise()))
            .build();

    // Without ambient brow motions, the brow moves only by reacting
    private static final AmbientAnimationLibrary WITHOUT_BROW_MOTIONS = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(STILL_BREATHING)
            .blink(AmbientAnimations.blink())
            .browMotions(List.of())
            .build();

    private static final LocomotionAnimationLibrary LOCOMOTION_LIBRARY = InMemoryLocomotionAnimationLibrary.builder()
            .standingPose(LocomotionAnimations.standingPose())
            .animations(Map.of(
                    NavigationType.STROLL, LocomotionAnimations.stroll(),
                    NavigationType.WALK, LocomotionAnimations.walk(),
                    NavigationType.RUN, LocomotionAnimations.run(),
                    NavigationType.SPRINT, LocomotionAnimations.sprint()))
            .build();

    // The longest first wait for an ambient brow motion and the longest motion it can start
    private static final long FIRST_BROW_MOTION_SPAN = AmbientBrow.MIN_DELAY_TICKS + AmbientBrow.DELAY_RANGE_TICKS
            + WITH_BROW_MOTIONS.browMotions().stream().mapToInt(KeyframeAnimation::getDurationTicks).max().orElseThrow()
            + 1;

    @Test
    void ambientBrow_movesTheBrowOfAVillagerStandingStill() {
        // Arrange: a settled look, so nothing but the ambient brow moves the brow
        VillagerAnimationPipeline pipeline = pipeline(WITH_BROW_MOTIONS);
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        boolean moved = false;

        // Act
        for (long age = AGE_TICKS; age <= AGE_TICKS + FIRST_BROW_MOTION_SPAN && !moved; age++) {
            pipeline.advance(looking(age, 0.0F).partialTicks(0.5F).build(), snapshot);
            moved = movesTheBrow(snapshot);
        }

        // Assert: an ambient brow the pipeline never updated or applied would leave the brow at rest
        assertTrue(moved, "no ambient brow motion played");
    }

    @Test
    void ambientBrowAndReaction_togetherRaiseTheBrowNoHigherThanTheProceduralLimit() {
        // Arrange: a villager holding its head straight while looking far aside, so the brow reacts throughout, with
        // ambient brow motions coming due
        VillagerAnimationPipeline pipeline = pipeline(WITH_BROW_MOTIONS);
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        boolean raised = false;

        for (long age = AGE_TICKS; age <= AGE_TICKS + 3L * FIRST_BROW_MOTION_SPAN; age++) {
            // Act
            pipeline.advance(holdingTheHead(age, WIDE_LOOK_YAW_DEGREES), snapshot);
            float offset = snapshot.getPose().get(BROW_TRANSLATION, Y);
            raised |= offset < -TOLERANCE;

            // Assert: without the limit the reaction and the ambient raise would lift the brow to the final bound
            assertTrue(offset >= BrowReaction.PROCEDURAL_RAISE_LIMIT, "the brow rose to " + offset + " at age " + age);
        }
        assertTrue(raised, "the brow never rose");
    }

    @Test
    void authoredRaisePastTheProceduralLimit_isKept_throughTheAmbientBrowAndTheReaction() {
        // Arrange: on one seed, a villager holding its head straight and its brow authored past the procedural limit
        // while looking far aside, and one standing idle and looking ahead, both with ambient brow raises coming due
        VillagerAnimationPipeline holding = pipeline(WITH_ONLY_THE_BROW_RAISE);
        VillagerAnimationPipeline control = pipeline(WITH_ONLY_THE_BROW_RAISE);
        VillagerAnimationSnapshot holdingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        boolean controlRaised = false;

        for (long age = AGE_TICKS; age <= AGE_TICKS + FIRST_BROW_MOTION_SPAN; age++) {
            // Act
            holding.advance(looking(age, WIDE_LOOK_YAW_DEGREES).motionArchetype(AnimationArchetype.EAT).build(),
                    holdingSnapshot);
            control.advance(looking(age, 0.0F).build(), controlSnapshot);
            controlRaised |= controlSnapshot.getPose().get(BROW_TRANSLATION, Y) < -TOLERANCE;

            // Assert: limiting against a brow read after the ambient brow, or against the procedural limit alone,
            // would move the authored raise
            assertEquals(AUTHORED_RAISE, holdingSnapshot.getPose().get(BROW_TRANSLATION, Y), 0.0F,
                    "offset at age " + age);
        }
        assertTrue(controlRaised, "no ambient brow raise came due");
    }

    @Test
    void browSuppression_hidesTheAmbientBrowAndTheReaction() {
        // Arrange: both villagers hold the head straight while looking aside, so the brow reacts throughout; one
        // hold suppresses the brow
        VillagerAnimationPipeline suppressed = pipeline(WITH_BROW_MOTIONS);
        VillagerAnimationPipeline control = pipeline(WITH_BROW_MOTIONS);
        VillagerAnimationSnapshot suppressedSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        boolean controlMoved = false;
        boolean suppressedMoved = false;

        // Act: long enough for an ambient brow motion to come due
        for (long age = AGE_TICKS; age <= AGE_TICKS + FIRST_BROW_MOTION_SPAN; age++) {
            suppressed.advance(looking(age, LOOK_YAW_DEGREES).motionArchetype(AnimationArchetype.WAVE).build(),
                    suppressedSnapshot);
            control.advance(looking(age, LOOK_YAW_DEGREES).motionArchetype(AnimationArchetype.INTERACT).build(),
                    controlSnapshot);
            controlMoved |= movesTheBrow(controlSnapshot);
            suppressedMoved |= movesTheBrow(suppressedSnapshot);
        }

        // Assert: either brow source ignoring the brow's remaining weight would move the suppressed brow
        assertTrue(controlMoved, "the control's brow never reacted");
        assertFalse(suppressedMoved, "a suppressed brow moved");
    }

    @Test
    void browReaction_keepsFollowingWhileSuppressed_soTheBrowMatchesOnceSuppressionLifts() {
        // Arrange: both villagers sweep their look side to side; one suppresses its brow for a while
        VillagerAnimationPipeline suppressed = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationPipeline control = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationSnapshot suppressedSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlSnapshot = new VillagerAnimationSnapshot();
        long trigger = AGE_TICKS;
        long lifted = trigger + SUPPRESSION_TICKS + 1;
        suppressed.advance(sweeping(trigger - 1).build(), suppressedSnapshot);
        control.advance(sweeping(trigger - 1).build(), controlSnapshot);
        boolean hidWhileSuppressed = false;
        boolean reactedAfterwards = false;

        for (long age = trigger; age <= lifted + SUPPRESSION_TICKS; age++) {
            // Act
            suppressed.advance(sweeping(age).motionArchetype(AnimationArchetype.THROW).motionGeneration((byte) 1)
                    .build(), suppressedSnapshot);
            control.advance(sweeping(age).build(), controlSnapshot);

            // Assert: a reaction paused under suppression would resume from where the brow was hidden
            if (age < trigger + SUPPRESSION_TICKS) {
                hidWhileSuppressed |= !movesTheBrow(suppressedSnapshot) && movesTheBrow(controlSnapshot);
            } else if (age >= lifted) {
                assertEquals(controlSnapshot.getPose().get(BROW_TRANSLATION, Y),
                        suppressedSnapshot.getPose().get(BROW_TRANSLATION, Y), 0.0F, "offset at age " + age);
                assertEquals(controlSnapshot.getPose().get(BROW_ROTATION, Z),
                        suppressedSnapshot.getPose().get(BROW_ROTATION, Z), 0.0F, "roll at age " + age);
                reactedAfterwards |= movesTheBrow(controlSnapshot);
            }
        }
        assertTrue(hidWhileSuppressed, "the suppression never hid a reacting brow");
        assertTrue(reactedAfterwards, "the brow never reacted after the suppression lifted");
    }

    @Test
    void observationalEvaluation_settlesTheBrowReactionOnItsTarget_asAFirstSightDoes() {
        // Arrange: a villager holding its head straight while its look swings aside, and one first seen that way
        VillagerAnimationPipeline holding = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationPipeline firstSeen = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationSnapshot liveSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot settledSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot observationalSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationObservation swung = holdingTheHead(AGE_TICKS + 1, WIDE_LOOK_YAW_DEGREES);
        holding.advance(holdingTheHead(AGE_TICKS, 0.0F), new VillagerAnimationSnapshot());
        holding.advance(swung, liveSnapshot);
        firstSeen.advance(swung, settledSnapshot);

        // Act
        holding.evaluateObservationally(swung, observationalSnapshot);

        // Assert: the live brow is still rising; the observational one sits on the target as after a reset
        float settledRoll = settledSnapshot.getPose().get(BROW_ROTATION, Z);
        float liveRoll = liveSnapshot.getPose().get(BROW_ROTATION, Z);
        assertTrue(liveRoll > TOLERANCE && liveRoll < settledRoll - TOLERANCE, "the live brow did not rise gradually");
        assertEquals(settledRoll, observationalSnapshot.getPose().get(BROW_ROTATION, Z), TOLERANCE);
        assertEquals(settledSnapshot.getPose().get(BROW_TRANSLATION, Y),
                observationalSnapshot.getPose().get(BROW_TRANSLATION, Y), TOLERANCE);
    }

    @Test
    void observationalEvaluation_midReaction_leavesTheLiveBrowReactionUntouched() {
        // Arrange: two villagers mid-reaction; the subject is also previewed looking elsewhere, turned, and upside
        // down
        VillagerAnimationPipeline subject = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationPipeline control = pipeline(WITHOUT_BROW_MOTIONS);
        for (VillagerAnimationPipeline pipeline : List.of(subject, control)) {
            pipeline.advance(holdingTheHead(AGE_TICKS, 0.0F), new VillagerAnimationSnapshot());
            pipeline.advance(holdingTheHead(AGE_TICKS + 1, WIDE_LOOK_YAW_DEGREES), new VillagerAnimationSnapshot());
        }
        VillagerAnimationObservation preview = looking(AGE_TICKS + 1, -60.0F)
                .partialTicks(1.0F)
                .headPitchDegrees(30.0F)
                .bodyHeadingDegrees(90.0F)
                .upsideDown(true)
                .motionArchetype(AnimationArchetype.INTERACT)
                .build();
        VillagerAnimationObservation next = holdingTheHead(AGE_TICKS + 2, WIDE_LOOK_YAW_DEGREES);
        VillagerAnimationSnapshot subjectNext = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot controlNext = new VillagerAnimationSnapshot();

        // Act
        subject.evaluateObservationally(preview, new VillagerAnimationSnapshot());
        subject.advance(next, subjectNext);
        control.advance(next, controlNext);

        // Assert: a preview that stepped or settled the live reaction would leave the subject's brow elsewhere
        assertTrue(movesTheBrow(controlNext), "the control's brow did not react");
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(controlNext.getPose().get(BROW_TRANSLATION, axis),
                    subjectNext.getPose().get(BROW_TRANSLATION, axis), 0.0F);
            assertEquals(controlNext.getPose().get(BROW_ROTATION, axis),
                    subjectNext.getPose().get(BROW_ROTATION, axis), 0.0F);
        }
    }

    @Test
    void upsideDown_mirrorsTheBrowReaction_andASettledLookLeavesTheBrowAtRest() {
        // Arrange: the same world turn of the look, upright and upside down, where vanilla negates the look it reports
        VillagerAnimationPipeline upright = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationPipeline upsideDown = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationPipeline settledUpsideDown = pipeline(WITHOUT_BROW_MOTIONS);
        VillagerAnimationSnapshot uprightSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot upsideDownSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot settledSnapshot = new VillagerAnimationSnapshot();
        upright.advance(looking(AGE_TICKS, 0.0F).build(), uprightSnapshot);
        upsideDown.advance(looking(AGE_TICKS, 0.0F).upsideDown(true).build(), upsideDownSnapshot);

        // Act
        upright.advance(looking(AGE_TICKS + 1, LOOK_YAW_DEGREES).build(), uprightSnapshot);
        upsideDown.advance(looking(AGE_TICKS + 1, -LOOK_YAW_DEGREES).upsideDown(true).build(), upsideDownSnapshot);
        settledUpsideDown.advance(looking(AGE_TICKS + 1, -LOOK_YAW_DEGREES).upsideDown(true).build(), settledSnapshot);

        // Assert: the brow tilts toward the look while the head trails it. A second flip of the reported look would
        // leave a residual on the settled villager and break the mirror
        float uprightRoll = uprightSnapshot.getPose().get(BROW_ROTATION, Z);
        assertTrue(uprightRoll > TOLERANCE, "a look to the right did not raise the brow's right end: " + uprightRoll);
        assertTrue(uprightSnapshot.getPose().get(BROW_TRANSLATION, Y) < -TOLERANCE, "a sideways look did not raise it");
        assertEquals(-uprightRoll, upsideDownSnapshot.getPose().get(BROW_ROTATION, Z), TOLERANCE);
        assertEquals(uprightSnapshot.getPose().get(BROW_TRANSLATION, Y),
                upsideDownSnapshot.getPose().get(BROW_TRANSLATION, Y), TOLERANCE);
        assertFalse(movesTheBrow(settledSnapshot), "a settled upside-down look moved the brow");
    }

    private static VillagerAnimationPipeline pipeline(AmbientAnimationLibrary ambientLibrary) {
        BakedAnimationRegistry registry = BakedAnimationRegistry.forVillager(ANIMATION_LIBRARY, IDLE_LIBRARY,
                ambientLibrary, LOCOMOTION_LIBRARY);
        return new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY, ambientLibrary, LOCOMOTION_LIBRARY,
                registry, new UmbrellaAnimator(0), new Random(RANDOM_SEED));
    }

    private static boolean movesTheBrow(VillagerAnimationSnapshot snapshot) {
        return Math.abs(snapshot.getPose().get(BROW_TRANSLATION, Y)) > TOLERANCE
                || Math.abs(snapshot.getPose().get(BROW_ROTATION, Z)) > TOLERANCE;
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

    /**
     * Returns an observation holding the head straight ahead at the given age and look yaw.
     */
    private static VillagerAnimationObservation holdingTheHead(long age, float netHeadYawDegrees) {
        return looking(age, netHeadYawDegrees).motionArchetype(AnimationArchetype.INTERACT).build();
    }

    /**
     * A villager looking side to side, never settling, so its brow keeps reacting.
     */
    private static VillagerAnimationObservations.ObservationBuilder sweeping(long age) {
        return looking(age, WIDE_LOOK_YAW_DEGREES * (float) Math.sin(age / 3.0D));
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
