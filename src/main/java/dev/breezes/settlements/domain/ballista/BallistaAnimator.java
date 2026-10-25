package dev.breezes.settlements.domain.ballista;

import dev.breezes.settlements.domain.animation.AnimationPose;
import dev.breezes.settlements.domain.animation.BakedAnimation;
import dev.breezes.settlements.domain.animation.BallistaAnimationTargets;
import dev.breezes.settlements.domain.animation.BallistaAnimations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Poses a ballista's rig from its state machine. The unwound and cocked stages hold still poses, the rest pose and the
 * wind clip's last frame, and winding and firing play their clips between them.
 * <p>
 * Only updateAnimationFromState changes what plays, so a pose sampled more than once in a tick cannot age the
 * animation. Instants are ticks on the same counter as the states passed in.
 */
public final class BallistaAnimator {

    private static final BakedAnimation WIND =
            BakedAnimation.bake(BallistaAnimations.wind(), BallistaAnimationTargets.RIG);
    private static final BakedAnimation FIRE =
            BakedAnimation.bake(BallistaAnimations.fire(), BallistaAnimationTargets.RIG);

    @Nullable
    private BakedAnimation playingClip;

    private long playingClipStartedAt;

    /**
     * Plays the clip of a winding or firing state from that state's own start instant, replacing whatever was playing.
     * A settled state changes nothing, so a clip already playing finishes.
     */
    public void updateAnimationFromState(@Nonnull BallistaStateMachine state) {
        if (!state.isTransient()) {
            return;
        }
        this.playingClip = state.getStage() == BallistaStateMachine.Stage.WINDING ? WIND : FIRE;
        this.playingClipStartedAt = state.getStartedAt();
    }

    /**
     * Replaces the pose with the rig's pose at the given instant, from whatever the last updateAnimationFromState left
     * playing: the playing clip through its last tick, then the still pose of the stage the state settles into by that
     * instant. An instant before the clip's start draws its first frame, and a partial tick that is not a number draws
     * the playing clip's last frame. Allocates nothing.
     *
     * @throws IllegalArgumentException if the pose is not on the ballista rig
     */
    public void sampleInto(@Nonnull AnimationPose pose,
                           @Nonnull BallistaStateMachine state,
                           long now,
                           float partialTick) {
        // Keep the successful rig check free of varargs allocation
        if (pose.getRig() != BallistaAnimationTargets.RIG) {
            throw new IllegalArgumentException("A ballista pose must be on rig "
                    + BallistaAnimationTargets.RIG.getName() + ", not rig " + pose.getRig().getName());
        }
        pose.resetToRest();

        // No crossfade is needed since every clip starts and ends on a pose the machine holds
        BakedAnimation clip = this.playingClip;
        if (clip != null) {
            float elapsedTicks = Math.max(0.0F, (now - this.playingClipStartedAt) + partialTick);
            // Over only once past its last tick, so the final frame draws on that tick; negated so NaN holds that frame
            if (!(elapsedTicks > clip.getDurationTicks())) {
                clip.sampleInto(pose, elapsedTicks);
                return;
            }
        }

        // The clip may have finished before the supplied state was settled
        if (state.settledAt(now).getStage() == BallistaStateMachine.Stage.COCKED) {
            WIND.sampleInto(pose, WIND.getDurationTicks());
        }
    }

}
