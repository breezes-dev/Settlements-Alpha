package dev.breezes.settlements.domain.animation;

import javax.annotation.Nonnull;

/**
 * Owns the umbrella's deploy/stow state machine, which commits a gate change after a delay, and samples the
 * overlapping carry and canopy clips it drives. Only updating changes the state; visibility and sampling are
 * read-only, and successful carry and canopy reads allocate no objects.
 */
public final class UmbrellaAnimator {

    private static final BakedAnimation DEPLOY =
            BakedAnimation.bake(UmbrellaAnimations.deploy(), UmbrellaAnimationTargets.RIG);
    private static final BakedAnimation UNDEPLOY =
            BakedAnimation.bake(UmbrellaAnimations.undeploy(), UmbrellaAnimationTargets.RIG);
    private static final BakedAnimation RAISE =
            BakedAnimation.bake(UmbrellaCarryAnimations.raise(), VillagerAnimationRig.RIG);
    private static final BakedAnimation LOWER =
            BakedAnimation.bake(UmbrellaCarryAnimations.lower(), VillagerAnimationRig.RIG);

    private enum Direction {
        DEPLOYING,
        RETRACTING
    }

    private final DelayedGate gate;

    private Direction direction = Direction.RETRACTING;

    // Treat retraction as complete at tick 0 so initial visibility needs no separate state
    private long directionStartTick = -UmbrellaCarryAnimations.LOWER_DURATION_TICKS;

    /**
     * Starts stowed, with a delay before committing each changed gate request; 0 commits immediately.
     */
    UmbrellaAnimator(int gateDelayTicks) {
        this.gate = new DelayedGate(gateDelayTicks, false);
    }

    /**
     * Advances the deploy/stow state machine toward the requested gate value. A direction change commits after the
     * gate delay, and returning to the current direction cancels the pending change. Repeating an update with the same
     * tick and gate value has no additional effect.
     * <p>
     * A committed change restarts playback rather than blending from the current pose, so reversing during motion may
     * cause a visible snap.
     */
    public void update(long tick, boolean shouldDeploy) {
        boolean committedGate = this.gate.update(tick, shouldDeploy);
        boolean currentlyDeploying = this.direction == Direction.DEPLOYING;
        if (committedGate == currentlyDeploying) {
            return;
        }

        this.direction = committedGate ? Direction.DEPLOYING : Direction.RETRACTING;
        this.directionStartTick = tick;
    }

    /**
     * Whether the attachment should be rendered at this sample.
     */
    public boolean isVisible(long tick, float partialTicks) {
        if (this.direction == Direction.DEPLOYING) {
            return true;
        }

        // Wait for both motions to finish so the attachment cannot disappear while the canopy is still closing
        float elapsedTicks = this.elapsedTicksSinceDirectionStart(tick, partialTicks);
        boolean carryFinished = elapsedTicks >= UmbrellaCarryAnimations.LOWER_DURATION_TICKS;
        float canopyElapsedTicks = elapsedTicks - UmbrellaCarryAnimations.LOWER_CANOPY_START_TICK;
        boolean canopyFinished = canopyElapsedTicks >= UmbrellaAnimations.UNDEPLOY_DURATION_TICKS;
        return !(carryFinished && canopyFinished);
    }

    /**
     * Replaces the contribution's content with the current direction's carry animation, including its arm claims.
     *
     * @throws IllegalArgumentException if the contribution is not on the villager rig
     */
    public void contributeCarry(@Nonnull AnimationContribution contribution, long tick, float partialTicks) {
        BakedAnimation carryClip = this.direction == Direction.DEPLOYING ? RAISE : LOWER;
        carryClip.contribute(contribution, this.elapsedTicksSinceDirectionStart(tick, partialTicks));
    }

    /**
     * Writes the current direction's canopy animation at this sample into the umbrella prop pose, leaving channels it
     * does not key unchanged.
     *
     * @throws IllegalArgumentException if the pose is not on the umbrella prop's rig
     */
    public void sampleCanopyInto(@Nonnull AnimationPose canopyPose, long tick, float partialTicks) {
        BakedAnimation canopyClip = this.direction == Direction.DEPLOYING ? DEPLOY : UNDEPLOY;
        float elapsedTicks = this.elapsedTicksSinceDirectionStart(tick, partialTicks);
        canopyClip.sampleInto(canopyPose, this.canopyElapsedTicks(elapsedTicks));
    }

    private float canopyElapsedTicks(float carryElapsedTicks) {
        int canopyStartTick = this.direction == Direction.DEPLOYING
                ? UmbrellaCarryAnimations.RAISE_CANOPY_START_TICK
                : UmbrellaCarryAnimations.LOWER_CANOPY_START_TICK;
        // Hold the initial canopy pose during the carry lead-in: furled when raising and open when lowering
        return Math.max(0.0F, carryElapsedTicks - canopyStartTick);
    }

    private float elapsedTicksSinceDirectionStart(long tick, float partialTicks) {
        return Math.max(0.0F, (tick - this.directionStartTick) + partialTicks);
    }

}
