package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import lombok.Getter;

import javax.annotation.Nonnull;

/**
 * The evaluated animation for one villager sample, reusable across evaluations.
 * Each evaluation replaces every value.
 */
@Getter
public final class VillagerAnimationSnapshot {

    /**
     * The resolved villager pose, rest-relative per channel kind.
     */
    private final AnimationPose pose = new AnimationPose(VillagerAnimationRig.RIG);

    /**
     * The umbrella prop's local pose, at rest while the umbrella is hidden.
     */
    private final AnimationPose umbrellaPose = new AnimationPose(UmbrellaAnimationTargets.RIG);

    private ArmConfiguration armConfiguration = ArmConfiguration.BOTH_CROSSED;

    /**
     * Whether the umbrella attachment is drawn.
     */
    private boolean umbrellaVisible;

    /**
     * Whether the lantern attachment is drawn.
     */
    private boolean lanternVisible;

    void setArmConfiguration(@Nonnull ArmConfiguration armConfiguration) {
        this.armConfiguration = armConfiguration;
    }

    void setUmbrellaVisible(boolean umbrellaVisible) {
        this.umbrellaVisible = umbrellaVisible;
    }

    void setLanternVisible(boolean lanternVisible) {
        this.lanternVisible = lanternVisible;
    }

}
