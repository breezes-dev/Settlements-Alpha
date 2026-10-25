package dev.breezes.settlements.domain.animation;

public interface UmbrellaAnimatorFactory {

    /**
     * Creates an umbrella animator with a randomly chosen gate delay.
     */
    UmbrellaAnimator create();

}
