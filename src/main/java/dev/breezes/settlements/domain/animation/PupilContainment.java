package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;

/**
 * Enforces pupil bounds in eyeball-local coordinates.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PupilContainment {

    /**
     * Minimum fraction of the pre-squash width or height retained at the white's inner or bottom edge.
     */
    static final float MIN_SQUASHED_SHARE = 0.75F;

    private static final float WHITE_WIDTH = 2.0F * VillagerEye.WHITE_HALF_WIDTH;
    // Eyeball-local y points down, so the white's top edge is its negative extent
    private static final float WHITE_TOP_Y = -VillagerEye.WHITE_HALF_HEIGHT;
    private static final float WHITE_BOTTOM_Y = VillagerEye.WHITE_HALF_HEIGHT;

    private static final int LEFT_TRANSLATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.PUPIL_LEFT_TRANSLATION);
    private static final int LEFT_SCALE = VillagerAnimationRig.RIG.indexOf(AnimationTargets.PUPIL_LEFT_SCALE);
    private static final int RIGHT_TRANSLATION = VillagerAnimationRig.RIG.indexOf(AnimationTargets.PUPIL_RIGHT_TRANSLATION);
    private static final int RIGHT_SCALE = VillagerAnimationRig.RIG.indexOf(AnimationTargets.PUPIL_RIGHT_SCALE);
    private static final int X = 0;
    private static final int Y = 1;

    /**
     * Adjusts each pupil's x and y translation and scale to satisfy the eye's bounds, measuring its dimensions from
     * its rest size times its resolved scale. Pupils already within the bounds remain unchanged, as does depth.
     * <p>
     * A pupil wider than the white is narrowed to fit. Outward movement stops at the white's outer edge. Inward and
     * downward movement squash against the white's inner and bottom edges, respectively, while the opposite edge
     * follows the requested translation until {@link #MIN_SQUASHED_SHARE} is reached. Upward movement may hide half
     * the pupil beneath the eyelid, with its center stopping at the white's top edge.
     *
     * @param pose pose on the villager rig; not validated
     */
    static void apply(@Nonnull AnimationPose pose) {
        containPupil(pose, VillagerEye.LEFT, LEFT_TRANSLATION, LEFT_SCALE);
        containPupil(pose, VillagerEye.RIGHT, RIGHT_TRANSLATION, RIGHT_SCALE);
    }

    private static void containPupil(@Nonnull AnimationPose pose,
                                     @Nonnull VillagerEye eye,
                                     int translation,
                                     int scale) {
        float width = VillagerEye.PUPIL_REST_SIZE * pose.get(scale, X);
        // Cap width before testing either edge; an oversized pupil cannot satisfy both horizontal bounds
        if (width > WHITE_WIDTH) {
            width = WHITE_WIDTH;
            pose.set(scale, X, WHITE_WIDTH / VillagerEye.PUPIL_REST_SIZE);
        }
        float restX = eye.pupilRestX();
        float innerEdgeX = eye.innerEdgeX();
        float outward = eye.getOutwardDirection();
        // Clamp translation directly to avoid rounding a valid authored offset through center-coordinate conversions
        float outwardStopX = -innerEdgeX - outward * width / 2.0F - restX;
        float translationX = pose.get(translation, X);
        if (outward * (translationX - outwardStopX) > 0.0F) {
            translationX = outwardStopX;
        }
        float pupilInnerEdgeX = restX + translationX - outward * width / 2.0F;
        if (outward * (innerEdgeX - pupilInnerEdgeX) > 0.0F) {
            // Keeping the inner edge fixed while the outer edge follows the push makes the pupil compress against
            // the middle of the face
            float squashedWidth = Math.max(outward * (pupilInnerEdgeX - innerEdgeX) + width,
                    MIN_SQUASHED_SHARE * width);
            translationX = innerEdgeX + outward * squashedWidth / 2.0F - restX;
            pose.set(scale, X, squashedWidth / VillagerEye.PUPIL_REST_SIZE);
        }
        pose.set(translation, X, translationX);

        float height = VillagerEye.PUPIL_REST_SIZE * pose.get(scale, Y);
        // Allowing half the pupil beneath the upper eyelid requires clamping its center rather than its top edge
        float translationY = Math.max(pose.get(translation, Y), WHITE_TOP_Y - VillagerEye.PUPIL_REST_Y);
        float bottomY = VillagerEye.PUPIL_REST_Y + translationY + height / 2.0F;
        if (bottomY > WHITE_BOTTOM_Y) {
            // Keep the bottom edge fixed so a downward push compresses the pupil and reveals more white above
            float topY = Math.min(bottomY - height, WHITE_BOTTOM_Y - MIN_SQUASHED_SHARE * height);
            float squashedHeight = WHITE_BOTTOM_Y - topY;
            translationY = WHITE_BOTTOM_Y - squashedHeight / 2.0F - VillagerEye.PUPIL_REST_Y;
            pose.set(scale, Y, squashedHeight / VillagerEye.PUPIL_REST_SIZE);
        }
        pose.set(translation, Y, translationY);
    }

}
