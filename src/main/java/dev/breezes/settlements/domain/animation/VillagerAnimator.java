package dev.breezes.settlements.domain.animation;

import lombok.Getter;

import javax.annotation.Nonnull;

/**
 * Coordinates live and observational animation for one villager, retaining its latest accepted snapshot.
 * Steady-state advances and evaluations allocate nothing.
 */
public final class VillagerAnimator {

    private final VillagerAnimationPipeline pipeline;

    /**
     * The observation the next advance reads.
     */
    @Getter
    private final VillagerAnimationObservation observation = new VillagerAnimationObservation();

    /**
     * The evaluation of the last accepted sample, at rest until the first advance.
     */
    @Getter
    private final VillagerAnimationSnapshot snapshot = new VillagerAnimationSnapshot();

    private boolean sampleAccepted;
    private long acceptedAgeTicks;
    private float acceptedPartialTicks;

    public VillagerAnimator(@Nonnull VillagerAnimationPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Whether this partial tick is eligible for live advancement. Held-still entities are always eligible; other
     * entities require a partial tick below one.
     *
     * @param heldStill whether the world holds the entity still this frame
     */
    public static boolean isLiveSample(float partialTicks, boolean heldStill) {
        return heldStill || partialTicks < 1.0F;
    }

    /**
     * Advances to the observation's sample key and returns this animator's reusable snapshot. A key equal to or earlier
     * than the last accepted one returns the existing snapshot unchanged, even if other observation fields differ.
     */
    public VillagerAnimationSnapshot advance() {
        long ageTicks = this.observation.getAgeTicks();
        float partialTicks = this.observation.getPartialTicks();
        // Repeated or older samples must not advance animation twice or rewind its committed state
        if (this.sampleAccepted && (ageTicks < this.acceptedAgeTicks
                || (ageTicks == this.acceptedAgeTicks && partialTicks <= this.acceptedPartialTicks))) {
            return this.snapshot;
        }
        this.sampleAccepted = true;
        this.acceptedAgeTicks = ageTicks;
        this.acceptedPartialTicks = partialTicks;
        this.pipeline.advance(this.observation, this.snapshot);
        return this.snapshot;
    }

    /**
     * Fills the given snapshot from the committed animation state at the supplied observation. The look has no
     * smoothing but retains its yaw limit, the nose has no procedural lag, and the brow reacts as fully as its look
     * residual calls for. Leaves live animation state and this animator's own observation and snapshot unchanged.
     *
     * @throws IllegalArgumentException if the given snapshot is this animator's own
     */
    public void evaluateObservationally(@Nonnull VillagerAnimationObservation observation,
                                        @Nonnull VillagerAnimationSnapshot snapshot) {
        // Avoid varargs validation helpers so observational evaluations allocate no argument array
        if (snapshot == this.snapshot) {
            throw new IllegalArgumentException(
                    "An observational evaluation must not overwrite the animator's snapshot");
        }
        this.pipeline.evaluateObservationally(observation, snapshot);
    }

}
