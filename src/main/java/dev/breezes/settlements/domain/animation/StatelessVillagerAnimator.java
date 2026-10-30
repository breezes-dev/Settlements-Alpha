package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;

import javax.annotation.Nonnull;

/**
 * Evaluates villager animation from the current observation without retaining animation history. Evaluations allocate
 * nothing.
 */
public final class StatelessVillagerAnimator {

    private final LookAndUnhappySlots lookAndUnhappy = new LookAndUnhappySlots();
    private final ComponentSuppression suppression = new ComponentSuppression();
    private final HeadTrail settledHeadTrail = new HeadTrail();
    // Leave this unmeasured so each evaluation resets its followers instead of retaining history
    private final FollowerStep settledStep = new FollowerStep();

    /**
     * Overwrites the snapshot with the observed look without smoothing, with yaw bounded by
     * {@link HeadTrail#MAX_DISPLAYED_YAW_RADIANS}. Adds the head shake at full weight when unhappy, resets every other
     * pose channel to rest, crosses both arms, and hides carried props.
     */
    public void evaluate(@Nonnull VillagerAnimationObservation observation,
                         @Nonnull VillagerAnimationSnapshot snapshot) {
        AnimationPose pose = snapshot.getPose();
        pose.resetToRest();
        this.suppression.reset();
        this.settledHeadTrail.follow(observation, this.settledStep);
        this.lookAndUnhappy.apply(pose, this.suppression, observation, this.settledHeadTrail,
                observation.isUnhappy() ? 1.0F : 0.0F);

        snapshot.setArmConfiguration(ArmConfiguration.BOTH_CROSSED);
        snapshot.setUmbrellaVisible(false);
        snapshot.setLanternVisible(false);
        snapshot.getUmbrellaPose().resetToRest();
    }

}
