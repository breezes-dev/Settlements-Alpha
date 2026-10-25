package dev.breezes.settlements.domain.animation;

/**
 * A pupil's edges in its eyeball's local units, where y points down, read from a pose on the villager rig.
 */
record PupilEdges(float minX, float maxX, float topY, float bottomY) {

    private static final int X = 0;
    private static final int Y = 1;

    static PupilEdges of(AnimationPose pose, VillagerEye eye) {
        int translation = VillagerAnimationRig.RIG.indexOf(eye == VillagerEye.LEFT
                ? AnimationTargets.PUPIL_LEFT_TRANSLATION
                : AnimationTargets.PUPIL_RIGHT_TRANSLATION);
        int scale = VillagerAnimationRig.RIG.indexOf(eye == VillagerEye.LEFT
                ? AnimationTargets.PUPIL_LEFT_SCALE
                : AnimationTargets.PUPIL_RIGHT_SCALE);
        float centerX = eye.pupilRestX() + pose.get(translation, X);
        float centerY = VillagerEye.PUPIL_REST_Y + pose.get(translation, Y);
        float halfWidth = VillagerEye.PUPIL_REST_SIZE * pose.get(scale, X) / 2.0F;
        float halfHeight = VillagerEye.PUPIL_REST_SIZE * pose.get(scale, Y) / 2.0F;
        return new PupilEdges(centerX - halfWidth, centerX + halfWidth, centerY - halfHeight, centerY + halfHeight);
    }

    float centerY() {
        return (this.topY + this.bottomY) / 2.0F;
    }

    float height() {
        return this.bottomY - this.topY;
    }

}
