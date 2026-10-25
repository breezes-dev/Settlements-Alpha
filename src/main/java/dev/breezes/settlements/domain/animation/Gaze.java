package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;

/**
 * Moves both pupils toward the look direction.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Gaze {

    // Pupil travel, in eyeball-local units, per unit of a head-facing component in model space
    private static final float OFFSET_PER_UNIT_OF_HEAD_FACING = 0.5F;
    // Pupil travel, in eyeball-local units, per unit of a look-residual component in the head's axes
    private static final float OFFSET_PER_UNIT_OF_RESIDUAL = 1.5F;

    private static final int LEFT_TRANSLATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.PUPIL_LEFT_TRANSLATION);
    private static final int RIGHT_TRANSLATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.PUPIL_RIGHT_TRANSLATION);
    private static final int X = 0;
    private static final int Y = 1;

    /**
     * Adds weight-scaled gaze offset to both pupils' existing x and y translations, leaving depth and scale unchanged.
     * The offset combines the drawn head's facing in model space with the look direction relative to the head.
     *
     * @param pose      pose on the villager rig; not validated
     * @param weight    fraction of the gaze to add, in [0, 1]; not validated
     * @param headFrame composed from this sample's pose and observation
     */
    static void apply(@Nonnull AnimationPose pose, float weight, @Nonnull HeadFrame headFrame) {
        // Keep the negated comparison so NaN cannot propagate into the pupil translations
        if (!(weight > 0.0F)) {
            return;
        }

        // Both inputs already account for the model's orientation; applying another upside-down flip would reverse
        // the gaze. Negate rightward components because the pupils' positive x points toward the villager's left
        float horizontalOffset = -OFFSET_PER_UNIT_OF_HEAD_FACING * headFrame.headFacingRightward()
                - OFFSET_PER_UNIT_OF_RESIDUAL * headFrame.lookResidualRightward();
        // The pupils' y points down, as the downward components do
        float verticalOffset = OFFSET_PER_UNIT_OF_HEAD_FACING * headFrame.headFacingDownward()
                + OFFSET_PER_UNIT_OF_RESIDUAL * headFrame.lookResidualDownward();
        addToTranslation(pose, LEFT_TRANSLATION, weight * horizontalOffset, weight * verticalOffset);
        addToTranslation(pose, RIGHT_TRANSLATION, weight * horizontalOffset, weight * verticalOffset);
    }

    private static void addToTranslation(@Nonnull AnimationPose pose,
                                         int translation,
                                         float horizontalOffset,
                                         float verticalOffset) {
        pose.set(translation, X, pose.get(translation, X) + horizontalOffset);
        pose.set(translation, Y, pose.get(translation, Y) + verticalOffset);
    }

}
