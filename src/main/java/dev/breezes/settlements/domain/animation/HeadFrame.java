package dev.breezes.settlements.domain.animation;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;

/**
 * Mutable orientation of the drawn head in model space, with the observed look expressed in the head's own axes.
 * Initially the head faces negative z with no rotation, and the look lies straight ahead of it.
 */
final class HeadFrame {

    // A forward direction this close to vertical, as a horizontal length, has no heading worth following
    private static final float MIN_HORIZONTAL_FORWARD_LENGTH = 0.1F;

    private static final int ROOT_ROTATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.ROOT_ROTATION);
    private static final int TORSO_ROTATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.BODY_ROTATION);
    private static final int HEAD_ROTATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.HEAD_ROTATION);

    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf boneRotation = new Quaternionf();
    private final Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
    private final Vector3f lookInHeadAxes = new Vector3f(0.0F, 0.0F, -1.0F);

    /**
     * Replaces the head orientation with the pose's combined root-to-head rotation, and expresses the observation's
     * look in that orientation's axes.
     *
     * @throws IllegalArgumentException if the pose is not on the villager rig
     */
    void compose(@Nonnull AnimationPose pose, @Nonnull VillagerAnimationObservation observation) {
        // Keep the successful rig check free of varargs allocation
        if (pose.getRig() != VillagerAnimationRig.RIG) {
            throw new IllegalArgumentException("A head frame composes a villager pose, not one on rig " + pose.getRig().getName());
        }

        // Compose parent-first. The unanimated part between root and torso and the chain's zero rest rotations
        // require no additional factors
        this.orientation.identity();
        this.multiplyBoneRotation(pose, ROOT_ROTATION);
        this.multiplyBoneRotation(pose, TORSO_ROTATION);
        this.multiplyBoneRotation(pose, HEAD_ROTATION);

        // The model's face points toward negative z
        this.orientation.transform(0.0F, 0.0F, -1.0F, this.forward);

        // The observed look is already body-relative and flipped as the model is, so it is a model-space direction:
        // where the head alone, rotated by it as the model rotates the head, would face
        this.boneRotation.rotationZYX(0.0F,
                observation.getNetHeadYawDegrees() * Mth.DEG_TO_RAD,
                observation.getHeadPitchDegrees() * Mth.DEG_TO_RAD);
        this.boneRotation.transform(0.0F, 0.0F, -1.0F, this.lookInHeadAxes);
        this.orientation.transformInverseUnit(this.lookInHeadAxes);
    }

    /**
     * Returns the model-space head heading in radians, positive toward the body's right. The result is undefined
     * while isHeadingDegenerate returns true.
     */
    float modelHeadingRadians() {
        return (float) Math.atan2(-this.forward.x(), -this.forward.z());
    }

    /**
     * Returns whether the head's forward direction is too close to vertical to provide a stable heading.
     */
    boolean isHeadingDegenerate() {
        float horizontalLengthSquared = this.forward.x() * this.forward.x() + this.forward.z() * this.forward.z();
        return !(horizontalLengthSquared >= MIN_HORIZONTAL_FORWARD_LENGTH * MIN_HORIZONTAL_FORWARD_LENGTH);
    }

    /**
     * The drawn head's facing rightward component in model space: how far its forward direction points toward the
     * body's right, in [-1, 1]. Zero while it faces straight ahead or straight up or down, and negative while it faces
     * to the left.
     */
    float headFacingRightward() {
        // The model's x axis runs toward the villager's left
        return -this.forward.x();
    }

    /**
     * The drawn head's facing downward component in model space: how far its forward direction points down, in
     * [-1, 1]. Zero while it faces level, and negative while it faces up.
     */
    float headFacingDownward() {
        return this.forward.y();
    }

    /**
     * The look residual's rightward component: how far the observed look's unit direction points toward the drawn
     * head's right, in [-1, 1]. Zero while the head faces the look, and negative while the look lies to its left.
     */
    float lookResidualRightward() {
        // The head's own x axis runs toward the villager's left
        return -this.lookInHeadAxes.x();
    }

    /**
     * The look residual's downward component: how far the observed look's unit direction points downward in the drawn
     * head's axes, in [-1, 1]. Zero while the head faces the look, and negative while the look lies above it.
     */
    float lookResidualDownward() {
        return this.lookInHeadAxes.y();
    }

    private void multiplyBoneRotation(@Nonnull AnimationPose pose, int rotationChannel) {
        // Preserve the model's Z, Y, X Euler order so combined pitch and roll produce the same heading
        this.orientation.mul(this.boneRotation.rotationZYX(
                pose.get(rotationChannel, 2), pose.get(rotationChannel, 1), pose.get(rotationChannel, 0)));
    }

}
