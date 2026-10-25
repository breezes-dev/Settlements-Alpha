package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.attachment.UmbrellaSlot;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerAnimatorTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180F;

    private static final long RANDOM_SEED = 7L;
    private static final int NO_GATE_DELAY_TICKS = 0;
    private static final long FIRST_SEEN_AGE_TICKS = 1_000L;

    private static final float LOOK_YAW_DEGREES = 30.0F;
    private static final float OTHER_LOOK_YAW_DEGREES = -45.0F;
    private static final float THIRD_LOOK_YAW_DEGREES = 60.0F;

    private static final int HEAD = VillagerAnimationRig.RIG.indexOf(AnimationTargets.HEAD_ROTATION);
    private static final int LEFT_UMBRELLA_SLOT = VillagerAnimationRig.RIG.indexOf(
            SlotTargets.translation(UmbrellaSlot.UMBRELLA_LEFT));

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), WaveAnimations.wave(),
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SleepingAnimations.sleeping()))
            .build();

    private static final IdleAnimationLibrary IDLE_LIBRARY = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of(IdleAnimations.ponder()))
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
    void advance_aSecondTimeAtOneSampleKey_changesNothing_whateverTheObservationNowHolds() {
        // Arrange: the control sees each key once; the subject's second pass at the key would wave, sleep, look
        // elsewhere, and raise the umbrella if it were taken as a sample
        VillagerAnimator subject = animator();
        VillagerAnimator control = animator();
        advance(subject, standing(FIRST_SEEN_AGE_TICKS, 0.5F));
        advance(control, standing(FIRST_SEEN_AGE_TICKS, 0.5F));
        SnapshotValues firstPass = SnapshotValues.of(subject.getSnapshot());

        // Act: the next sample looks elsewhere, so an animator that refused it would show the old look
        SnapshotValues secondPass = SnapshotValues.of(advance(subject, everythingElse(FIRST_SEEN_AGE_TICKS, 0.5F)));
        VillagerAnimationSnapshot subjectNext = advance(subject, lookingElsewhere(FIRST_SEEN_AGE_TICKS + 1, 0.0F));
        VillagerAnimationSnapshot controlNext = advance(control, lookingElsewhere(FIRST_SEEN_AGE_TICKS + 1, 0.0F));

        // Assert
        firstPass.assertSameAs(secondPass);
        assertTurnedTowardElsewhere(subjectNext);
        SnapshotValues.of(controlNext).assertSameAs(subjectNext);
    }

    @Test
    void advance_atAKeyBehindTheLastAcceptedOne_changesNothing() {
        // Arrange
        VillagerAnimator subject = animator();
        VillagerAnimator control = animator();
        advance(subject, standing(FIRST_SEEN_AGE_TICKS, 0.5F));
        advance(control, standing(FIRST_SEEN_AGE_TICKS, 0.5F));
        SnapshotValues accepted = SnapshotValues.of(subject.getSnapshot());

        // Act: changed inputs on rejected keys must not affect the next accepted sample
        VillagerAnimationSnapshot earlierInTheTick = advance(subject, everythingElse(FIRST_SEEN_AGE_TICKS, 0.25F));
        SnapshotValues earlierInTheTickValues = SnapshotValues.of(earlierInTheTick);
        VillagerAnimationSnapshot tickBefore = advance(subject, everythingElse(FIRST_SEEN_AGE_TICKS - 1, 0.9F));
        SnapshotValues tickBeforeValues = SnapshotValues.of(tickBefore);
        VillagerAnimationSnapshot subjectNext = advance(subject, lookingElsewhere(FIRST_SEEN_AGE_TICKS + 1, 0.0F));
        VillagerAnimationSnapshot controlNext = advance(control, lookingElsewhere(FIRST_SEEN_AGE_TICKS + 1, 0.0F));

        // Assert
        accepted.assertSameAs(earlierInTheTickValues);
        accepted.assertSameAs(tickBeforeValues);
        assertTurnedTowardElsewhere(subjectNext);
        SnapshotValues.of(controlNext).assertSameAs(subjectNext);
    }

    @Test
    void advance_laterInTheSameTick_acceptsANewSample() {
        // Arrange
        VillagerAnimator animator = animator();
        advance(animator, standing(FIRST_SEEN_AGE_TICKS, 0.25F));

        // Act
        VillagerAnimationSnapshot later = advance(animator, lookingElsewhere(FIRST_SEEN_AGE_TICKS, 0.5F));

        // Assert
        assertTurnedTowardElsewhere(later);
    }

    @Test
    void advance_onTheNextTickAtALowerPartialTick_acceptsANewSample() {
        // Arrange
        VillagerAnimator animator = animator();
        advance(animator, standing(FIRST_SEEN_AGE_TICKS, 0.8F));

        // Act
        VillagerAnimationSnapshot nextTick = advance(animator, lookingElsewhere(FIRST_SEEN_AGE_TICKS + 1, 0.1F));

        // Assert
        assertTurnedTowardElsewhere(nextTick);
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, false, true",
            "0.99, false, true",
            "1.0, false, false",
            "1.5, false, false",
            "NaN, false, false",
            "1.0, true, true"})
    void isLiveSample_refusesAWholeTickOrMore_unlessTheEntityIsHeldStill(float partialTicks,
                                                                          boolean heldStill,
                                                                          boolean live) {
        // Act, Assert
        assertEquals(live, VillagerAnimator.isLiveSample(partialTicks, heldStill));
    }

    @Test
    void evaluateObservationally_fillsItsOwnSnapshot_atItsOwnKey_andCommitsNothing() {
        // Arrange: raising the umbrella, so its carry moves between two partial ticks of one tick
        VillagerAnimator subject = animator();
        VillagerAnimator control = animator();
        long raising = FIRST_SEEN_AGE_TICKS + UmbrellaCarryAnimations.RAISE_CANOPY_START_TICK / 2;
        for (long age = FIRST_SEEN_AGE_TICKS; age <= raising; age++) {
            advance(subject, standing(age, 0.0F).umbrellaRequested(true));
            advance(control, standing(age, 0.0F).umbrellaRequested(true));
        }
        SnapshotValues live = SnapshotValues.of(subject.getSnapshot());
        // Conflicting preview inputs expose accidental changes to the live action, sleep, mood, and umbrella state
        VillagerAnimationObservation preview = everythingElse(raising, 1.0F).umbrellaRequested(false).build();
        VillagerAnimationSnapshot previewSnapshot = new VillagerAnimationSnapshot();

        // Act
        subject.evaluateObservationally(preview, previewSnapshot);

        // Assert: the preview reads its own look and its own umbrella pose, and the live villager goes on unchanged
        assertTrue(previewSnapshot.isUmbrellaVisible());
        assertLooksElsewhere(previewSnapshot);
        assertNotEquals(subject.getSnapshot().getPose().get(LEFT_UMBRELLA_SLOT, 1),
                previewSnapshot.getPose().get(LEFT_UMBRELLA_SLOT, 1), TOLERANCE);
        live.assertSameAs(subject.getSnapshot());
        VillagerAnimationSnapshot subjectNext = advance(subject, standing(raising, 0.5F)
                .netHeadYawDegrees(THIRD_LOOK_YAW_DEGREES).umbrellaRequested(true));
        VillagerAnimationSnapshot controlNext = advance(control, standing(raising, 0.5F)
                .netHeadYawDegrees(THIRD_LOOK_YAW_DEGREES).umbrellaRequested(true));
        // A committed preview would change the starting yaw of this turn
        float subjectNextYaw = subjectNext.getPose().get(HEAD, 1);
        assertTrue(subjectNextYaw > LOOK_YAW_DEGREES * DEGREES_TO_RADIANS + TOLERANCE, "the next sample was refused");
        assertTrue(subjectNextYaw < THIRD_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, "the head overshot the third look");
        SnapshotValues.of(controlNext).assertSameAs(subjectNext);
    }

    @Test
    void evaluateObservationally_intoTheAnimatorsOwnSnapshot_isRefused() {
        // Arrange
        VillagerAnimator animator = animator();
        advance(animator, standing(FIRST_SEEN_AGE_TICKS, 0.0F));

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> animator.evaluateObservationally(
                standing(FIRST_SEEN_AGE_TICKS, 1.0F).build(), animator.getSnapshot()));
    }

    private static VillagerAnimator animator() {
        return new VillagerAnimator(new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY,
                LOCOMOTION_LIBRARY, REGISTRY, new UmbrellaAnimator(NO_GATE_DELAY_TICKS), new Random(RANDOM_SEED)));
    }

    private static VillagerAnimationSnapshot advance(VillagerAnimator animator,
                                                     VillagerAnimationObservations.ObservationBuilder observation) {
        VillagerAnimationObservations.copyInto(observation.build(), animator.getObservation());
        return animator.advance();
    }

    /**
     * An awake, contented villager standing still with no action, looking level and to one side.
     */
    private static VillagerAnimationObservations.ObservationBuilder standing(long age, float partialTicks) {
        return VillagerAnimationObservations.observation()
                .ageTicks(age)
                .partialTicks(partialTicks)
                .netHeadYawDegrees(LOOK_YAW_DEGREES)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic());
    }

    private static VillagerAnimationObservations.ObservationBuilder lookingElsewhere(long age, float partialTicks) {
        return standing(age, partialTicks).netHeadYawDegrees(OTHER_LOOK_YAW_DEGREES);
    }

    private static void assertLooksElsewhere(VillagerAnimationSnapshot snapshot) {
        assertEquals(OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, snapshot.getPose().get(HEAD, 1), TOLERANCE);
    }

    /**
     * Asserts that the head yaw lies strictly between the initial and changed look yaws.
     */
    private static void assertTurnedTowardElsewhere(VillagerAnimationSnapshot snapshot) {
        float yaw = snapshot.getPose().get(HEAD, 1);
        assertTrue(yaw < LOOK_YAW_DEGREES * DEGREES_TO_RADIANS - TOLERANCE, "the head held the first look");
        assertTrue(yaw > OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, "the head turned past the other look");
    }

    /**
     * Builds conflicting animation inputs at the supplied sample key.
     */
    private static VillagerAnimationObservations.ObservationBuilder everythingElse(long age, float partialTicks) {
        return standing(age, partialTicks)
                .netHeadYawDegrees(OTHER_LOOK_YAW_DEGREES)
                .motionArchetype(AnimationArchetype.WAVE)
                .motionGeneration((byte) 1)
                .sleeping(true)
                .unhappy(true)
                .umbrellaRequested(true);
    }

    /**
     * Every value of a snapshot, copied so a later evaluation cannot change it.
     */
    private record SnapshotValues(ArmConfiguration armConfiguration,
                                  boolean umbrellaVisible,
                                  float[] pose,
                                  float[] umbrellaPose) {

        static SnapshotValues of(VillagerAnimationSnapshot snapshot) {
            return new SnapshotValues(snapshot.getArmConfiguration(), snapshot.isUmbrellaVisible(),
                    valuesOf(snapshot.getPose()), valuesOf(snapshot.getUmbrellaPose()));
        }

        void assertSameAs(VillagerAnimationSnapshot snapshot) {
            this.assertSameAs(of(snapshot));
        }

        void assertSameAs(SnapshotValues other) {
            assertEquals(this.armConfiguration, other.armConfiguration);
            assertEquals(this.umbrellaVisible, other.umbrellaVisible);
            assertArrayEquals(this.pose, other.pose, 0.0F);
            assertArrayEquals(this.umbrellaPose, other.umbrellaPose, 0.0F);
        }

        private static float[] valuesOf(AnimationPose pose) {
            float[] values = new float[pose.getRig().getChannelCount() * AnimationPose.COMPONENTS];
            for (int channel = 0; channel < pose.getRig().getChannelCount(); channel++) {
                for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                    values[channel * AnimationPose.COMPONENTS + axis] = pose.get(channel, axis);
                }
            }
            return values;
        }

    }

}
