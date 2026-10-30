package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies per-arm arbitration and locomotion selection through observable pose values.
 */
class VillagerAnimationPipelineSlotTest {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int TORSO = RIG.indexOf(AnimationTargets.BODY_TRANSLATION);
    private static final float TOLERANCE = 1.0E-5F;
    private static final long AGE_TICKS = 1_000L;
    private static final long RANDOM_SEED = 7L;

    // Vanilla's walk animation swings the limbs through one full cycle over this much limb swing
    private static final float VANILLA_LIMB_SWING_CYCLE = (float) ((Math.PI * 2.0D) / 0.6662D);

    private static final int GAIT_DURATION_TICKS = 20;

    // The walk's torso height is its clip tick, so a sample shows where in the cycle it played
    private static final KeyframeAnimation RAMPING_WALK = KeyframeAnimation.fromTracks()
            .id(ResourceLocationUtil.mod("animation/test/ramping_walk"))
            .durationTicks(GAIT_DURATION_TICKS)
            .loopMode(LoopMode.LOOP)
            .owns(VillagerAnimationRig.LEG_ROTATION)
            .arms(ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT)
            .track(torsoHeight(new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                    new Keyframe<>(GAIT_DURATION_TICKS, new Vec3(0.0, GAIT_DURATION_TICKS, 0.0), Easing.LINEAR)))
            .build();

    // Each other gait holds a torso height of its own, so the height shows which gait played
    private static final Map<NavigationType, KeyframeAnimation> GAITS = gaits();

    private static final KeyframeAnimation SLEEP_CROSSED =
            clipClaiming("sleep_crossed", ArmPose.CROSSED, ArmPose.CROSSED);
    private static final KeyframeAnimation WAVE_LEFT_CROSSED =
            clipClaiming("wave_left_crossed", ArmPose.CROSSED, null);
    private static final KeyframeAnimation INTERACT_STRAIGHT =
            clipClaiming("interact_straight", ArmPose.STRAIGHT, ArmPose.STRAIGHT);

    private static final AnimationLibrary ACTIONS = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), SLEEP_CROSSED,
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), WAVE_LEFT_CROSSED,
                    AnimationKey.of(AnimationArchetype.INTERACT, ItemCategory.GENERIC), INTERACT_STRAIGHT))
            .build();

    @Test
    void armClaims_takeEachArmFromTheHighestSlotClaimingIt() {
        // Arrange: the action claims only the left arm, over a standing pose that claims both straight
        VillagerAnimationPipeline pipeline = pipeline(standingClaiming(ArmPose.STRAIGHT, ArmPose.STRAIGHT));
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        step(pipeline, standing(AGE_TICKS), snapshot);

        // Act
        step(pipeline, acting(AGE_TICKS + 1, AnimationArchetype.WAVE), snapshot);

        // Assert: arbitrating whole configurations would take both arms from one slot
        assertEquals(ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT, snapshot.getArmConfiguration());
    }

    @Test
    void armClaims_preferTheHigherSlot_whenTwoClaimTheSameArm() {
        // Arrange: asleep, whose clip claims both arms crossed, while an action claims both straight
        VillagerAnimationPipeline pipeline = pipeline(standingClaiming(null, null));
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();
        step(pipeline, standing(AGE_TICKS), snapshot);
        step(pipeline, acting(AGE_TICKS + 1, AnimationArchetype.INTERACT), snapshot);

        // Act
        VillagerAnimationObservation asleep = acting(AGE_TICKS + 2, AnimationArchetype.INTERACT);
        asleep.setSleeping(true);
        step(pipeline, asleep, snapshot);

        // Assert
        assertEquals(ArmConfiguration.BOTH_CROSSED, snapshot.getArmConfiguration());
    }

    @Test
    void armClaims_crossAnArm_thatNoSlotClaims() {
        // Arrange: a standing pose that claims only the left arm, with nothing above it
        VillagerAnimationPipeline pipeline = pipeline(standingClaiming(ArmPose.STRAIGHT, null));
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

        // Act
        step(pipeline, standing(AGE_TICKS), snapshot);

        // Assert
        assertEquals(ArmConfiguration.RIGHT_CROSSED_LEFT_STRAIGHT, snapshot.getArmConfiguration());
    }

    @Test
    void armClaims_comeFromTheGait_whileMoving_andFromTheStandingPose_atAStandstill() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline(standingClaiming(ArmPose.STRAIGHT, ArmPose.STRAIGHT));
        VillagerAnimationSnapshot standingSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot walkingSnapshot = new VillagerAnimationSnapshot();

        // Act
        pipeline.evaluateObservationally(standing(AGE_TICKS), standingSnapshot);
        pipeline.evaluateObservationally(walking(AGE_TICKS, NavigationType.WALK, 0.0F), walkingSnapshot);

        // Assert
        assertEquals(ArmConfiguration.BOTH_STRAIGHT, standingSnapshot.getArmConfiguration());
        assertEquals(ArmConfiguration.LEFT_CROSSED_RIGHT_STRAIGHT, walkingSnapshot.getArmConfiguration());
    }

    @Test
    void gait_isTheOneItsNavigationTypeSelects() {
        // Arrange
        VillagerAnimationPipeline pipeline = pipeline(standingClaiming(null, null));
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

        for (NavigationType navigationType : NavigationType.values()) {
            if (navigationType == NavigationType.WALK) {
                continue;
            }

            // Act
            pipeline.evaluateObservationally(walking(AGE_TICKS, navigationType, 0.0F), snapshot);

            // Assert
            assertEquals(heightOf(navigationType), snapshot.getPose().get(TORSO, 1), TOLERANCE, navigationType.name());
        }
    }

    @Test
    void gait_playsOneClipCycle_perVanillaLimbCycle() {
        // Arrange: a quarter of a vanilla limb cycle, which distance walked sets whatever the speed
        VillagerAnimationPipeline pipeline = pipeline(standingClaiming(null, null));
        VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

        // Act
        pipeline.evaluateObservationally(walking(AGE_TICKS, NavigationType.WALK, VANILLA_LIMB_SWING_CYCLE / 4.0F),
                snapshot);

        // Assert: the ramping walk's height is the clip tick it played
        assertEquals(GAIT_DURATION_TICKS / 4.0F, snapshot.getPose().get(TORSO, 1), TOLERANCE);
    }

    private static VillagerAnimationPipeline pipeline(KeyframeAnimation standingPose) {
        IdleAnimationLibrary idle = InMemoryIdleAnimationLibrary.builder()
                .idleAnimations(List.of())
                .build();
        AmbientAnimationLibrary ambient = InMemoryAmbientAnimationLibrary.builder()
                .breathingCycle(AmbientAnimations.breathingCycle())
                .blink(AmbientAnimations.blink())
                .browMotions(List.of())
                .build();
        LocomotionAnimationLibrary locomotion = InMemoryLocomotionAnimationLibrary.builder()
                .standingPose(standingPose)
                .animations(GAITS)
                .build();
        return new VillagerAnimationPipeline(ACTIONS, idle, ambient, locomotion,
                BakedAnimationRegistry.forVillager(ACTIONS, idle, ambient, locomotion), new UmbrellaAnimator(0),
                new LanternAnimator(0), new Random(RANDOM_SEED));
    }

    private static void step(VillagerAnimationPipeline pipeline,
                             VillagerAnimationObservation observation,
                             VillagerAnimationSnapshot snapshot) {
        pipeline.advance(observation, snapshot);
    }

    private static VillagerAnimationObservations.ObservationBuilder observation(long age) {
        return VillagerAnimationObservations.observation()
                .ageTicks(age)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic());
    }

    private static VillagerAnimationObservation standing(long age) {
        return observation(age).build();
    }

    /**
     * Returns an action observation with a nonzero trigger generation.
     */
    private static VillagerAnimationObservation acting(long age, AnimationArchetype archetype) {
        return observation(age).motionArchetype(archetype).motionGeneration((byte) 1).build();
    }

    private static VillagerAnimationObservation walking(long age, NavigationType navigationType, float limbSwing) {
        return observation(age)
                .navigationType(navigationType)
                .limbSwing(limbSwing)
                .limbSwingAmount(VillagerAnimationPipeline.FULL_WEIGHT_LIMB_SWING_AMOUNT)
                .build();
    }

    private static KeyframeAnimation standingClaiming(@Nullable ArmPose left, @Nullable ArmPose right) {
        return clipClaiming("standing", left, right);
    }

    private static KeyframeAnimation clipClaiming(String name, @Nullable ArmPose left, @Nullable ArmPose right) {
        TrackAnimationBuilder builder = KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/" + name))
                .durationTicks(GAIT_DURATION_TICKS)
                .loopMode(LoopMode.LOOP);
        if (left != null) {
            builder.leftArm(left);
        }
        if (right != null) {
            builder.rightArm(right);
        }
        return builder.build();
    }

    private static Map<NavigationType, KeyframeAnimation> gaits() {
        Map<NavigationType, KeyframeAnimation> gaits = new EnumMap<>(NavigationType.class);
        for (NavigationType navigationType : NavigationType.values()) {
            String name = "animation/test/gait_" + navigationType.name().toLowerCase(Locale.ROOT);
            gaits.put(navigationType, navigationType == NavigationType.WALK ? RAMPING_WALK
                    : KeyframeAnimation.fromTracks()
                    .id(ResourceLocationUtil.mod(name))
                    .durationTicks(GAIT_DURATION_TICKS)
                    .loopMode(LoopMode.LOOP)
                    .owns(VillagerAnimationRig.LEG_ROTATION)
                    .track(torsoHeight(new Keyframe<>(0, new Vec3(0.0, heightOf(navigationType), 0.0), Easing.LINEAR)))
                    .build());
        }
        return gaits;
    }

    private static float heightOf(NavigationType navigationType) {
        return 100.0F + navigationType.ordinal();
    }

    @SafeVarargs
    private static AnimationTrack<Vec3> torsoHeight(Keyframe<Vec3>... keyframes) {
        return AnimationTrack.<Vec3>builder()
                .target(AnimationTargets.BODY_TRANSLATION)
                .keyframes(List.of(keyframes))
                .build();
    }

}
