package dev.breezes.settlements.infrastructure.rendering.animation;

import dev.breezes.settlements.di.ClientSessionScope;
import dev.breezes.settlements.domain.animation.AmbientAnimationLibrary;
import dev.breezes.settlements.domain.animation.AnimationLibrary;
import dev.breezes.settlements.domain.animation.BakedAnimationRegistry;
import dev.breezes.settlements.domain.animation.IdleAnimationLibrary;
import dev.breezes.settlements.domain.animation.LanternAnimator;
import dev.breezes.settlements.domain.animation.LocomotionAnimationLibrary;
import dev.breezes.settlements.domain.animation.UmbrellaAnimatorFactory;
import dev.breezes.settlements.domain.animation.VillagerAnimationPipeline;
import dev.breezes.settlements.domain.animation.VillagerAnimator;
import dev.breezes.settlements.shared.util.RandomUtil;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * Builds a villager's client animator from the session's animation collaborators.
 */
@ClientSessionScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class VillagerAnimatorFactory {

    private final AnimationLibrary animationLibrary;
    private final IdleAnimationLibrary idleAnimationLibrary;
    private final AmbientAnimationLibrary ambientAnimationLibrary;
    private final LocomotionAnimationLibrary locomotionAnimationLibrary;
    private final BakedAnimationRegistry bakedAnimationRegistry;
    private final UmbrellaAnimatorFactory umbrellaAnimatorFactory;

    /**
     * Creates the animator for one villager.
     */
    public VillagerAnimator create() {
        return new VillagerAnimator(new VillagerAnimationPipeline(
                this.animationLibrary,
                this.idleAnimationLibrary,
                this.ambientAnimationLibrary,
                this.locomotionAnimationLibrary,
                this.bakedAnimationRegistry,
                this.umbrellaAnimatorFactory.create(),
                LanternAnimator.create(RandomUtil.RANDOM),
                RandomUtil.RANDOM));
    }

}
