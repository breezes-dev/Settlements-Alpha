package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.time.ClockTicks;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Owns the lantern's hold, show, stow, and yield transitions.
 * Only updates change its state; visibility and carry reads are observational.
 */
public final class LanternAnimator {

    /**
     * Longest per-villager delay before a changed request commits.
     */
    public static final int MAX_GATE_DELAY_TICKS = ClockTicks.seconds(8).getTicksAsInt();

    /**
     * Uninterrupted time without a yield before a return may begin.
     */
    public static final int RETURN_SETTLE_TICKS = ClockTicks.seconds(1).getTicksAsInt();

    /**
     * Time for an interrupted carry pose to fade to rest.
     */
    public static final int YIELD_FADE_TICKS = ClockTicks.seconds(0.2).getTicksAsInt();

    private static final BakedAnimation HOLD = BakedAnimation.bake(LanternCarryAnimations.hold(), VillagerAnimationRig.RIG);
    private static final BakedAnimation SHOW = BakedAnimation.bake(LanternCarryAnimations.show(), VillagerAnimationRig.RIG);
    private static final BakedAnimation STOW = BakedAnimation.bake(LanternCarryAnimations.stow(), VillagerAnimationRig.RIG);

    private static final int ARMS_CROSSED_ROTATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.ARMS_CROSSED_ROTATION);

    private enum State {
        HIDDEN,
        HOLDING,
        SHOWING,
        STOWING,
        YIELDED
    }

    private final DelayedGate gate;
    private final AnimationContribution interruptedCarry = new AnimationContribution(VillagerAnimationRig.RIG);

    private boolean checkDecided;
    private State state = State.HIDDEN;
    private long gestureStartTick;
    private long yieldStartTick;
    private float yieldStartPartialTicks;
    private float interruptedPitch;
    private boolean settling;
    private long yieldClearTick;

    /**
     * Creates a hidden animator with a fixed delay for subsequent gate changes.
     *
     * @throws IllegalArgumentException if gateDelayTicks is negative
     */
    LanternAnimator(int gateDelayTicks) {
        Validate.isTrue(gateDelayTicks >= 0, "gateDelayTicks must be nonnegative");
        this.gate = new DelayedGate(gateDelayTicks, false);
    }

    /**
     * Creates an animator with a fixed delay drawn once from the supplied random source.
     */
    public static LanternAnimator create(@Nonnull Random random) {
        return new LanternAnimator(random.nextInt(MAX_GATE_DELAY_TICKS + 1));
    }

    /**
     * Advances the hold, show, stow, and yield transitions to this sample.
     * Repeating an update with the same sample and inputs has no further effect.
     *
     * @param lanternCheckDecided whether the client has sampled the lantern conditions yet; nothing changes until it has
     * @param requested           whether the lantern conditions ask for the lantern
     * @param yielding            whether something the lantern yields to holds at this sample
     */
    public void update(long tick, float partialTicks, boolean lanternCheckDecided, boolean requested, boolean yielding) {
        if (!this.checkDecided) {
            if (!lanternCheckDecided) {
                return;
            }

            this.checkDecided = true;
            this.gate.reset(requested);
            this.state = requested && !yielding ? State.HOLDING : State.HIDDEN;
        } else {
            this.gate.update(tick, requested);
        }

        if (yielding) {
            if (this.state != State.YIELDED) {
                // Freeze the interrupted pose so the fade cannot jump to a gesture endpoint.
                this.contributeCarry(this.interruptedCarry, tick, partialTicks);
                this.interruptedPitch = this.interruptedCarry.value(ARMS_CROSSED_ROTATION, 0);
                this.yieldStartTick = tick;
                this.yieldStartPartialTicks = partialTicks;
                this.state = State.YIELDED;
            }
            this.settling = false;
            return;
        }

        if (this.state == State.YIELDED) {
            this.updateReturn(tick, partialTicks, requested);
            return;
        }

        if (this.state == State.SHOWING || this.state == State.STOWING) {
            if (tick - this.gestureStartTick < InteractAnimations.INTERACT_DURATION_TICKS) {
                return;
            }
            // Complete at the shared endpoint before honoring a reversal that committed mid-gesture.
            this.state = this.state == State.SHOWING ? State.HOLDING : State.HIDDEN;
        }

        if (this.state == State.HIDDEN && this.gate.isOpen()) {
            this.state = State.SHOWING;
            this.gestureStartTick = tick;
        } else if (this.state == State.HOLDING && !this.gate.isOpen()) {
            this.state = State.STOWING;
            this.gestureStartTick = tick;
        }
    }

    /**
     * Whether the lantern is visible at this sample, including the exact fractional-tick gesture peak.
     */
    public boolean isVisible(long tick, float partialTicks) {
        return switch (this.state) {
            case HOLDING -> true;
            case SHOWING -> this.elapsedGestureTicks(tick, partialTicks) >= InteractAnimations.INTERACT_PEAK_TICK;
            case STOWING -> this.elapsedGestureTicks(tick, partialTicks) < InteractAnimations.INTERACT_PEAK_TICK;
            case HIDDEN, YIELDED -> false;
        };
    }

    /**
     * Replaces the contribution with the sampled carry pose, owning no channels and claiming no arms.
     *
     * @throws IllegalArgumentException if the contribution is not on the villager rig
     */
    public void contributeCarry(@Nonnull AnimationContribution contribution, long tick, float partialTicks) {
        if (contribution.getRig() != VillagerAnimationRig.RIG) {
            throw new IllegalArgumentException("Lantern carry requires the villager rig");
        }
        switch (this.state) {
            case HOLDING -> HOLD.contribute(contribution, 0.0F);
            case SHOWING -> SHOW.contribute(contribution, this.elapsedGestureTicks(tick, partialTicks));
            case STOWING -> STOW.contribute(contribution, this.elapsedGestureTicks(tick, partialTicks));
            case HIDDEN -> contribution.clear();
            case YIELDED -> {
                contribution.clear();
                float elapsedTicks = this.elapsedYieldTicks(tick, partialTicks);
                float remainingWeight = 1.0F - Easing.EASE_OUT.apply(elapsedTicks / YIELD_FADE_TICKS);
                contribution.key(ARMS_CROSSED_ROTATION, this.interruptedPitch * remainingWeight, 0.0F, 0.0F);
            }
        }
    }

    private void updateReturn(long tick, float partialTicks, boolean requested) {
        if (!this.settling) {
            this.settling = true;
            this.yieldClearTick = tick;
        }
        boolean settled = tick - this.yieldClearTick >= RETURN_SETTLE_TICKS;
        boolean fadeFinished = this.elapsedYieldTicks(tick, partialTicks) >= YIELD_FADE_TICKS;
        // The raw request prevents a stale open gate from bringing out a lantern only to stow it.
        if (settled && fadeFinished && this.gate.isOpen() && requested) {
            this.state = State.SHOWING;
            this.gestureStartTick = tick;
        }
    }

    private float elapsedGestureTicks(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.gestureStartTick) + partialTicks);
    }

    private float elapsedYieldTicks(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.yieldStartTick) + (partialTicks - this.yieldStartPartialTicks));
    }

}
