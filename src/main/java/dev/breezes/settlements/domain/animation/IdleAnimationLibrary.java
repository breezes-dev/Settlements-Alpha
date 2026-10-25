package dev.breezes.settlements.domain.animation;

import java.util.List;

public interface IdleAnimationLibrary {

    /**
     * The occasional motions a villager may play while standing idle.
     */
    List<KeyframeAnimation> idleAnimations();

}
