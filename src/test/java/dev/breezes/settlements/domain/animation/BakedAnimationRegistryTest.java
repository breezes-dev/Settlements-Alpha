package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BakedAnimationRegistryTest {

    private static final KeyframeAnimation WAVE = WaveAnimations.wave();

    private static final AnimationLibrary ANIMATION_LIBRARY = InMemoryAnimationLibrary.builder()
            .animations(Map.of(AnimationKey.of(AnimationArchetype.WAVE, ItemCategory.GENERIC), WAVE))
            .build();

    private static final IdleAnimationLibrary IDLE_LIBRARY = InMemoryIdleAnimationLibrary.builder()
            .idleAnimations(List.of(IdleAnimations.ponder()))
            .build();

    private static final AmbientAnimationLibrary AMBIENT_LIBRARY = InMemoryAmbientAnimationLibrary.builder()
            .breathingCycle(AmbientAnimations.breathingCycle())
            .blink(AmbientAnimations.blink())
            .browMotions(List.of(AmbientAnimations.browRaise(), AmbientAnimations.browRaiseRightEnd()))
            .build();

    private static final LocomotionAnimationLibrary LOCOMOTION = InMemoryLocomotionAnimationLibrary.builder()
            .standingPose(LocomotionAnimations.standingPose())
            .animations(Map.of(
                    NavigationType.STROLL, LocomotionAnimations.stroll(),
                    NavigationType.WALK, LocomotionAnimations.walk(),
                    NavigationType.RUN, LocomotionAnimations.jog(),
                    NavigationType.SPRINT, LocomotionAnimations.run(),
                    NavigationType.PANIC, LocomotionAnimations.panicRun()))
            .build();

    @Test
    void baked_findsEveryClipTheLibrariesHandOut() {
        // Arrange
        BakedAnimationRegistry registry =
                BakedAnimationRegistry.forVillager(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION);

        // Act, Assert: a lookup throws for any instance missing from the registry
        for (AnimationArchetype archetype : AnimationArchetype.values()) {
            for (ItemCategory category : ItemCategory.values()) {
                KeyframeAnimation clip = ANIMATION_LIBRARY.resolve(archetype, new AnimationSelectionContext(category));
                assertSame(clip.getId(), registry.baked(clip).getId());
            }
        }
        for (NavigationType navigationType : NavigationType.values()) {
            KeyframeAnimation gait = LOCOMOTION.resolve(navigationType);
            assertSame(gait.getId(), registry.baked(gait).getId());
        }
        for (KeyframeAnimation libraryClip : List.of(LOCOMOTION.standingPose(),
                IDLE_LIBRARY.idleAnimations().getFirst(), AMBIENT_LIBRARY.breathingCycle(), AMBIENT_LIBRARY.blink())) {
            assertSame(libraryClip.getId(), registry.baked(libraryClip).getId());
        }
        for (KeyframeAnimation browMotion : AMBIENT_LIBRARY.browMotions()) {
            assertSame(browMotion.getId(), registry.baked(browMotion).getId());
        }
    }

    @Test
    void baked_rejectsAnInstanceItWasNotBuiltFrom_evenWithTheSameContent() {
        // Arrange
        BakedAnimationRegistry registry =
                BakedAnimationRegistry.forVillager(ANIMATION_LIBRARY, IDLE_LIBRARY, AMBIENT_LIBRARY, LOCOMOTION);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> registry.baked(WaveAnimations.wave()));
    }

}
