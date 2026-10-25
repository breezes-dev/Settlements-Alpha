package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the pupil containment bounds the pupils the pipeline resolves, whichever slot moved them.
 */
class VillagerAnimationPipelinePupilTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final long RANDOM_SEED = 7L;
    private static final long AGE_TICKS = 1_000L;

    // Positive x is the left pupil's outward direction and positive y points down; both reach far past the white
    private static final Vec3 FAR_OUTWARD_AND_DOWN = new Vec3(10.0, 10.0, 0.0);

    private static final KeyframeAnimation STARING_ACTION = staring("staring_action").build();
    private static final KeyframeAnimation STARING_SLEEP = staring("staring_sleep").playExclusively().build();

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(
                    AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), STARING_ACTION,
                    AnimationKey.of(AnimationArchetype.SLEEP, ItemCategory.GENERIC), STARING_SLEEP))
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

    @Test
    void containment_boundsAPupilAClipPushesPastTheWhite_awakeAndAsleep() {
        // Arrange: one villager holding the staring action, and one asleep in the staring sleep
        VillagerAnimationPipeline awake = pipeline();
        VillagerAnimationPipeline asleep = pipeline();
        VillagerAnimationSnapshot awakeSnapshot = new VillagerAnimationSnapshot();
        VillagerAnimationSnapshot asleepSnapshot = new VillagerAnimationSnapshot();

        // Act
        awake.advance(observation().motionArchetype(AnimationArchetype.WAVE).build(), awakeSnapshot);
        asleep.advance(observation().sleeping(true).build(), asleepSnapshot);

        // Assert: containment skipped under sleep, or applied before a slot that moves the pupils, would leave the
        // pupil past the white
        for (VillagerAnimationSnapshot snapshot : List.of(awakeSnapshot, asleepSnapshot)) {
            PupilEdges edges = PupilEdges.of(snapshot.getPose(), VillagerEye.LEFT);
            assertEquals(VillagerEye.WHITE_HALF_WIDTH, edges.maxX(), TOLERANCE);
            assertEquals(VillagerEye.WHITE_HALF_HEIGHT, edges.bottomY(), TOLERANCE);
        }
    }

    private static TrackAnimationBuilder staring(String name) {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/" + name))
                .durationTicks(20)
                .loopMode(LoopMode.LOOP)
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.PUPIL_LEFT_TRANSLATION)
                        .keyframes(List.of(new Keyframe<>(0, FAR_OUTWARD_AND_DOWN, Easing.LINEAR)))
                        .build());
    }

    private static VillagerAnimationPipeline pipeline() {
        BakedAnimationRegistry registry = BakedAnimationRegistry.forVillager(ANIMATION_LIBRARY, IDLE_LIBRARY,
                AMBIENT_LIBRARY, LOCOMOTION_LIBRARY);
        return new VillagerAnimationPipeline(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION_LIBRARY,
                registry, new UmbrellaAnimator(0), new Random(RANDOM_SEED));
    }

    /**
     * An awake, contented villager standing still with no action and looking straight ahead.
     */
    private static VillagerAnimationObservations.ObservationBuilder observation() {
        return VillagerAnimationObservations.observation()
                .ageTicks(AGE_TICKS)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic());
    }

}
