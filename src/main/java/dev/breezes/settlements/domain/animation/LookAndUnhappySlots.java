package dev.breezes.settlements.domain.animation;

import net.minecraft.util.Mth;

import javax.annotation.Nonnull;

/**
 * Composes the base head pose from a displayed look and a weighted unhappy shake.
 */
final class LookAndUnhappySlots {

    private static final float UNHAPPY_HEAD_PITCH_RADIANS = 0.4F;
    private static final float UNHAPPY_HEAD_ROLL_AMPLITUDE_RADIANS = 0.3F;
    private static final float UNHAPPY_HEAD_ROLL_FREQUENCY = 0.45F;

    private static final int HEAD_ROTATION_CHANNEL = VillagerAnimationRig.RIG.indexOf(AnimationTargets.HEAD_ROTATION);

    private final AnimationContribution look = new AnimationContribution(VillagerAnimationRig.RIG);
    private final AnimationContribution unhappyShake = new AnimationContribution(VillagerAnimationRig.RIG);

    LookAndUnhappySlots() {
        this.look.own(VillagerAnimationRig.HEAD_ROTATION, 1.0F);
    }

    /**
     * Sets the pose's head rotation to the displayed look and adds the unhappy shake at the given weight.
     * A weight of zero or less omits the shake.
     */
    void apply(@Nonnull AnimationPose pose,
               @Nonnull ComponentSuppression suppression,
               @Nonnull VillagerAnimationObservation observation,
               @Nonnull HeadTrail headTrail,
               float unhappyWeight) {
        this.look.key(HEAD_ROTATION_CHANNEL,
                headTrail.displayedPitchRadians(observation),
                headTrail.displayedYawRadians(observation),
                0.0F);
        AnimationCompositor.applyContribution(pose, suppression, 1.0F, this.look);

        if (unhappyWeight > 0.0F) {
            // Use one float age so the shake's phase, including its rounding, matches vanilla
            float ageInTicks = observation.getAgeTicks() + observation.getPartialTicks();
            float roll = UNHAPPY_HEAD_ROLL_AMPLITUDE_RADIANS * Mth.sin(UNHAPPY_HEAD_ROLL_FREQUENCY * ageInTicks);
            this.unhappyShake.key(HEAD_ROTATION_CHANNEL, UNHAPPY_HEAD_PITCH_RADIANS, 0.0F, roll);
            AnimationCompositor.applyContribution(pose, suppression, unhappyWeight, this.unhappyShake);
        }
    }

}
