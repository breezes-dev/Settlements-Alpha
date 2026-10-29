package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StatelessVillagerAnimatorTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180F;

    private static final long AGE_TICKS = 1_234L;
    private static final float LOOK_YAW_DEGREES = 30.0F;
    private static final float LOOK_PITCH_DEGREES = -10.0F;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int HEAD = RIG.indexOf(AnimationTargets.HEAD_ROTATION);

    @Test
    void evaluate_drawsTheLookOnTheHead_withEveryOtherValueAtRest_whateverTheSnapshotHeld() {
        // Arrange: stale pose and presentation values expose any output that evaluation fails to overwrite
        StatelessVillagerAnimator animator = new StatelessVillagerAnimator();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        animator.evaluate(looking().unhappy(true).build(), snapshot);
        dirty(snapshot);

        // Act
        animator.evaluate(looking().build(), snapshot);

        // Assert
        AnimationPose pose = snapshot.getPose();
        assertEquals(LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS, pose.get(HEAD, 0), TOLERANCE);
        assertEquals(LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, pose.get(HEAD, 1), TOLERANCE);
        assertEquals(0.0F, pose.get(HEAD, 2), TOLERANCE);
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            if (channel != HEAD) {
                assertAtRest(pose, channel);
            }
        }
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration());
        assertFalse(snapshot.isUmbrellaVisible());
        for (int channel = 0; channel < UmbrellaAnimationTargets.RIG.getChannelCount(); channel++) {
            assertAtRest(snapshot.getUmbrellaPose(), channel);
        }
    }

    @Test
    void evaluate_drawsTheObservedLook_whateverTheBodyHeading_upsideDownIncluded() {
        // Arrange: vanilla has already negated an upside-down villager's look
        StatelessVillagerAnimator animator = new StatelessVillagerAnimator();
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

        // Act
        animator.evaluate(looking()
                .netHeadYawDegrees(-LOOK_YAW_DEGREES)
                .headPitchDegrees(-LOOK_PITCH_DEGREES)
                .bodyHeadingDegrees(123.0F)
                .upsideDown(true)
                .build(), snapshot);

        // Assert: a head left in world heading would add the body heading, and a flip applied once would negate it
        AnimationPose pose = snapshot.getPose();
        assertEquals(-LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS, pose.get(HEAD, 0), TOLERANCE);
        assertEquals(-LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, pose.get(HEAD, 1), TOLERANCE);
    }

    @Test
    void evaluate_drawsTheWholeUnhappyShake_whileUnhappy() {
        // Arrange: a pipeline first seen unhappy draws the shake at full weight at once
        VillagerAnimationObservation unhappy = looking().unhappy(true).build();
        VillagerAnimationPipeline pipeline = pipeline();
        VillagerAnimationSnapshot pipelineSnapshot = new VillagerAnimationSnapshot();
        pipeline.advance(unhappy, pipelineSnapshot);
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

        // Act
        new StatelessVillagerAnimator().evaluate(unhappy, snapshot);

        // Assert
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(pipelineSnapshot.getPose().get(HEAD, axis), snapshot.getPose().get(HEAD, axis), TOLERANCE);
        }
    }

    @Test
    void evaluate_ignoresSleepTheRainGateAnActionAndMovement() {
        // Arrange
        StatelessVillagerAnimator animator = new StatelessVillagerAnimator();
        VillagerAnimationSnapshot content = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot busy = new VillagerAnimationSnapshot();
        animator.evaluate(looking().build(), content);

        // Act
        animator.evaluate(looking()
                .sleeping(true)
                .umbrellaRequested(true)
                .motionArchetype(AnimationArchetype.WAVE)
                .motionGeneration((byte) 1)
                .navigationType(NavigationType.RUN)
                .limbSwing(3.0F)
                .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT)
                .build(), busy);

        // Assert
        assertEquals(content.getArmConfiguration(), busy.getArmConfiguration());
        assertEquals(content.isUmbrellaVisible(), busy.isUmbrellaVisible());
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                assertEquals(content.getPose().get(channel, axis), busy.getPose().get(channel, axis), 0.0F,
                        RIG.channel(channel).getId() + " axis " + axis);
            }
        }
    }

    private static VillagerAnimationObservations.ObservationBuilder looking() {
        return VillagerAnimationObservations.observation()
                .ageTicks(AGE_TICKS)
                .netHeadYawDegrees(LOOK_YAW_DEGREES)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic());
    }

    private static void dirty(VillagerAnimationSnapshot snapshot) {
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            snapshot.getPose().set(channel, 0.5F, 0.5F, 0.5F);
        }
        for (int channel = 0; channel < UmbrellaAnimationTargets.RIG.getChannelCount(); channel++) {
            snapshot.getUmbrellaPose().set(channel, 0.5F, 0.5F, 0.5F);
        }
        snapshot.setArmConfiguration(ArmConfiguration.BOTH_STRAIGHT);
        snapshot.setUmbrellaVisible(true);
    }

    private static void assertAtRest(AnimationPose pose, int channel) {
        float rest = pose.getRig().kind(channel).getRest();
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            assertEquals(rest, pose.get(channel, axis), 0.0F, pose.getRig().channel(channel).getId() + " axis " + axis);
        }
    }

    private static VillagerAnimationPipeline pipeline() {
        AnimationLibrary animations = InMemoryAnimationLibrary.builder()
                .animations(Map.of(AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC),
                        SleepingAnimations.sleeping()))
                .build();
        IdleAnimationLibrary idle = InMemoryIdleAnimationLibrary.builder()
                .idleAnimations(List.of())
                .build();
        AmbientAnimationLibrary ambient = InMemoryAmbientAnimationLibrary.builder()
                .breathingCycle(AmbientAnimations.breathingCycle())
                .blink(AmbientAnimations.blink())
                .browMotions(List.of())
                .build();
        LocomotionAnimationLibrary locomotion = InMemoryLocomotionAnimationLibrary.builder()
                .standingPose(LocomotionAnimations.standingPose())
                .animations(Map.of(
                        NavigationType.STROLL, LocomotionAnimations.stroll(),
                        NavigationType.WALK, LocomotionAnimations.walk(),
                        NavigationType.RUN, LocomotionAnimations.run(),
                        NavigationType.SPRINT, LocomotionAnimations.sprint()))
                .build();
        return new VillagerAnimationPipeline(animations, idle, ambient, locomotion,
                BakedAnimationRegistry.forVillager(animations, idle, ambient, locomotion), new UmbrellaAnimator(0),
                new Random(1L));
    }

}
