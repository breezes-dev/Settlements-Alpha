package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Playback state for one animation slot, retaining at most one outgoing animation during a replacement crossfade.
 * Times are whole ticks plus a partial tick, and every time passed in must come from a single clock.
 * <p>
 * Weight, expiry, and playback-state queries are read-only. Only updating retires a completed crossfade.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnimationLayer {

    private static final long NOT_CLEARING = Long.MIN_VALUE;

    @Getter
    private KeyframeAnimation currentAnimation;
    private long currentStartTick;
    private boolean transientLayer;
    @Getter
    @Nullable
    private KeyframeAnimation outgoingAnimation;
    private long outgoingStartTick;
    private long clearStartTick;
    private float clearWeightFloor;

    /**
     * Layer weight at the start of the current animation's entry fade.
     */
    private float entryWeightFloor;

    /**
     * Creates a layer playing the animation from the start tick until it is cleared.
     */
    public static AnimationLayer persistent(@Nonnull KeyframeAnimation animation, long startTick) {
        return new AnimationLayer(animation, startTick, false, null, 0L, NOT_CLEARING, 0.0F, 0.0F);
    }

    /**
     * Restarts this layer over with the animation, which a transient layer plays once and a persistent one holds
     * until cleared. Allocates nothing.
     */
    public void restart(@Nonnull KeyframeAnimation animation, long startTick, boolean transientLayer) {
        this.currentAnimation = animation;
        this.currentStartTick = startTick;
        this.transientLayer = transientLayer;
        this.outgoingAnimation = null;
        this.outgoingStartTick = 0L;
        this.clearStartTick = NOT_CLEARING;
        this.clearWeightFloor = 0.0F;
        this.entryWeightFloor = 0.0F;
    }

    /**
     * Replaces the current animation, retaining it as the only outgoing animation.
     * Adopts the requested lifetime and cancels any blend-out started by clearing the layer.
     */
    public void replace(@Nonnull KeyframeAnimation animation, long tick, boolean transientLayer) {
        // Preserve the weight before replacement so an interrupted fade does not restart from zero
        this.entryWeightFloor = this.weight(tick, 0.0F);
        this.outgoingAnimation = this.currentAnimation;
        this.outgoingStartTick = this.currentStartTick;
        this.currentAnimation = animation;
        this.currentStartTick = tick;
        // Lifetime follows the replacement: a transient animation must expire even when the layer began persistent
        this.transientLayer = transientLayer;
        this.clearStartTick = NOT_CLEARING;
        this.clearWeightFloor = 0.0F;
    }

    /**
     * Swaps the current animation while preserving its playback start, the layer's lifetime mode, and any outgoing animation.
     * Fades and expiration use the new animation's timing.
     * <p>
     * A clear in progress keeps its start tick and fades from the weight the new animation would have had at that tick.
     */
    public void swapCurrentAnimation(@Nonnull KeyframeAnimation animation) {
        this.currentAnimation = animation;
        if (this.clearStartTick != NOT_CLEARING) {
            this.clearWeightFloor = this.weightBeforeClear(this.clearStartTick, 0.0F);
        }
    }

    /**
     * Starts this layer's blend-out from its current weight. Repeated calls leave an ongoing blend-out unchanged.
     */
    public void beginClear(long tick) {
        if (this.clearStartTick != NOT_CLEARING) {
            // Restarting the clock would extend the fade whenever clearing is requested again
            return;
        }
        this.clearWeightFloor = this.weight(tick, 0.0F);
        this.clearStartTick = tick;
    }

    /**
     * Retires the outgoing animation once the current one has fully blended in.
     */
    public void update(long tick, float partialTicks) {
        if (this.outgoingAnimation != null && this.blendProgress(tick, partialTicks) >= 1.0F) {
            this.outgoingAnimation = null;
        }
    }

    /**
     * Elapsed playback ticks since the current animation started, clamped at zero.
     */
    public float currentElapsedTicks(long tick, float partialTicks) {
        return this.elapsedTicks(tick, partialTicks);
    }

    /**
     * Elapsed playback ticks since the outgoing animation started, clamped at zero. Meaningful only while an
     * outgoing animation is retained.
     */
    public float outgoingElapsedTicks(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.outgoingStartTick) + partialTicks);
    }

    /**
     * The current animation's share of the crossfade, in [0, 1], rising with the same blend-in progress as the layer's
     * entry weight. The outgoing animation holds the remainder. Returns 1 when no outgoing animation is retained.
     */
    public float incomingShare(long tick, float partialTicks) {
        if (this.outgoingAnimation == null) {
            return 1.0F;
        }
        return this.blendProgress(tick, partialTicks);
    }

    /**
     * This layer's contribution weight after its entry fade and any automatic or explicit exit fade.
     */
    public float weight(long tick, float partialTicks) {
        float weight = this.weightBeforeClear(tick, partialTicks);
        if (this.clearStartTick != NOT_CLEARING) {
            weight = Math.min(weight, this.clearWeightFloor
                    * this.blendOutWeight(this.elapsedSinceClear(tick, partialTicks)));
        }
        return weight;
    }

    public boolean isExpired(long tick, float partialTicks) {
        if (this.clearStartTick != NOT_CLEARING
                && this.elapsedSinceClear(tick, partialTicks) >= this.currentAnimation.getBlendOutTicks()) {
            return true;
        }
        if (!this.transientLayer) {
            return false;
        }

        // A blend-out reaches zero at duration + blendOut, making that boundary expired. With no blend-out,
        // preserve the final playback tick and expire only after it.
        int blendOutTicks = this.currentAnimation.getBlendOutTicks();
        float expiryTick = this.currentAnimation.getDurationTicks() + blendOutTicks;
        float elapsedTicks = this.elapsedTicks(tick, partialTicks);
        return blendOutTicks <= 0
                ? elapsedTicks > expiryTick
                : elapsedTicks >= expiryTick;
    }

    /**
     * Returns the layer's weight with the entry fade and any automatic blend-out applied, excluding any clear fade.
     */
    private float weightBeforeClear(long tick, float partialTicks) {
        float weight = this.entryWeightFloor + (1.0F - this.entryWeightFloor) * this.blendProgress(tick, partialTicks);

        float elapsedTicks = this.elapsedTicks(tick, partialTicks);
        if (this.transientLayer && elapsedTicks > this.currentAnimation.getDurationTicks()) {
            weight = Math.min(weight, this.blendOutWeight(elapsedTicks - this.currentAnimation.getDurationTicks()));
        }
        return weight;
    }

    private float blendOutWeight(float elapsedBlendOutTicks) {
        int blendOutTicks = this.currentAnimation.getBlendOutTicks();
        if (blendOutTicks <= 0) {
            return 0.0F;
        }
        return 1.0F - Math.clamp(elapsedBlendOutTicks / blendOutTicks, 0.0F, 1.0F);
    }

    /**
     * Returns the current animation's blend-in progress, or 1 when no blend-in duration is configured.
     */
    private float blendProgress(long tick, float partialTicks) {
        int blendInTicks = this.currentAnimation.getBlendInTicks();
        if (blendInTicks <= 0) {
            return 1.0F;
        }

        return Math.clamp(this.elapsedTicks(tick, partialTicks) / blendInTicks, 0.0F, 1.0F);
    }

    private float elapsedTicks(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.currentStartTick) + partialTicks);
    }

    private float elapsedSinceClear(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.clearStartTick) + partialTicks);
    }

}
