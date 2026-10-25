package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.presentation.ItemCategory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import javax.annotation.Nonnull;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registry of animations baked for the villager rig, indexed by source clip instance.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BakedAnimationRegistry {

    private final Map<KeyframeAnimation, BakedAnimation> bakedByClip;

    /**
     * Creates a registry containing the animation library's results for every archetype and item-category pair,
     * the locomotion library's standing pose and its results for every navigation type, every idle animation, and
     * every clip of the ambient animation library. Each distinct clip instance is baked once for this registry.
     *
     * @throws IllegalArgumentException if a clip keys or owns a channel the villager rig lacks,
     *                                  or multiple tracks key the same channel
     */
    public static BakedAnimationRegistry forVillager(@Nonnull AnimationLibrary animationLibrary,
                                                     @Nonnull IdleAnimationLibrary idleAnimationLibrary,
                                                     @Nonnull AmbientAnimationLibrary ambientAnimationLibrary,
                                                     @Nonnull LocomotionAnimationLibrary locomotionLibrary) {
        // Distinct clip instances must remain separate even when they share an ID
        Map<KeyframeAnimation, BakedAnimation> bakedByClip = new IdentityHashMap<>();
        for (AnimationArchetype archetype : AnimationArchetype.values()) {
            for (ItemCategory category : ItemCategory.values()) {
                bakeOnce(bakedByClip, animationLibrary.resolve(archetype, new AnimationSelectionContext(category)));
            }
        }
        bakeOnce(bakedByClip, locomotionLibrary.standingPose());
        for (NavigationType navigationType : NavigationType.values()) {
            bakeOnce(bakedByClip, locomotionLibrary.resolve(navigationType));
        }
        for (KeyframeAnimation idleAnimation : idleAnimationLibrary.idleAnimations()) {
            bakeOnce(bakedByClip, idleAnimation);
        }
        bakeOnce(bakedByClip, ambientAnimationLibrary.breathingCycle());
        bakeOnce(bakedByClip, ambientAnimationLibrary.blink());
        for (KeyframeAnimation browMotion : ambientAnimationLibrary.browMotions()) {
            bakeOnce(bakedByClip, browMotion);
        }
        return new BakedAnimationRegistry(bakedByClip);
    }

    /**
     * Returns the baked animation registered for the given clip instance.
     * Successful lookups allocate no objects.
     *
     * @throws IllegalArgumentException if the clip instance is not registered
     */
    public BakedAnimation baked(@Nonnull KeyframeAnimation clip) {
        BakedAnimation baked = this.bakedByClip.get(clip);
        // Avoid varargs validation helpers so successful lookups allocate no argument array
        if (baked == null) {
            throw new IllegalArgumentException("No baked clip registered for " + clip.getId());
        }
        return baked;
    }

    private static void bakeOnce(@Nonnull Map<KeyframeAnimation, BakedAnimation> bakedByClip,
                                 @Nonnull KeyframeAnimation clip) {
        bakedByClip.computeIfAbsent(clip, unbaked -> BakedAnimation.bake(unbaked, VillagerAnimationRig.RIG));
    }

}
