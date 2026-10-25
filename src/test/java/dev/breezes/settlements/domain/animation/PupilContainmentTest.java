package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PupilContainmentTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int LEFT_TRANSLATION = RIG.indexOf(AnimationTargets.PUPIL_LEFT_TRANSLATION);
    private static final int LEFT_SCALE = RIG.indexOf(AnimationTargets.PUPIL_LEFT_SCALE);
    private static final int RIGHT_TRANSLATION = RIG.indexOf(AnimationTargets.PUPIL_RIGHT_TRANSLATION);
    private static final int RIGHT_SCALE = RIG.indexOf(AnimationTargets.PUPIL_RIGHT_SCALE);
    private static final int LEFT_EYEBALL_TRANSLATION = RIG.indexOf(AnimationTargets.EYEBALL_LEFT_TRANSLATION);
    private static final int LEFT_EYEBALL_SCALE = RIG.indexOf(AnimationTargets.EYEBALL_LEFT_SCALE);

    // Far past every edge of the white, whatever its size
    private static final float FAR_PUSH = 10.0F;

    // A narrowed pupil's resolved width, and a squashed one's resolved height, as an expression would key them
    private static final float NARROWED_SCALE = 0.5F;
    private static final float SQUASHED_SCALE = 0.7F;

    // Edges combine a translation and a scale whose keys and easings differ, so the sweep samples between ticks too
    private static final int SWEEP_SAMPLES_PER_TICK = 4;

    @Test
    void pupilsInsideTheBounds_areLeftExactlyAsTheyAre() {
        // Arrange: one pupil at rest, and one nudged and narrowed a little, with depth keyed on both
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_TRANSLATION, 0.0F, 0.0F, 0.3F);
        pose.set(LEFT_SCALE, 1.0F, 1.0F, 1.2F);
        pose.set(RIGHT_TRANSLATION, -0.02F, 0.02F, -0.1F);
        pose.set(RIGHT_SCALE, 0.9F, 0.8F, 0.7F);
        AnimationPose before = copyOf(pose);

        // Act
        PupilContainment.apply(pose);

        // Assert: re-deriving a contained value from its center or height would drift it, and a rest pupil past the
        // white would be squashed on every villager
        for (int channel : new int[]{LEFT_TRANSLATION, LEFT_SCALE, RIGHT_TRANSLATION, RIGHT_SCALE}) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                assertEquals(before.get(channel, axis), pose.get(channel, axis), 0.0F, "channel " + channel);
            }
        }
    }

    @Test
    void pupilPushedOutward_stopsAtTheWhitesOuterEdge_unsquashed_inEachEye() {
        // Arrange: x runs toward the villager's left, so each eye's outward push has its own sign
        AnimationPose outward = new AnimationPose(RIG);
        outward.set(LEFT_TRANSLATION, X, FAR_PUSH);
        outward.set(RIGHT_TRANSLATION, X, -FAR_PUSH);

        // Act
        PupilContainment.apply(outward);

        // Assert: bounding both pupils from one eye's rest position would stop the other a whole pupil off its edge,
        // and a squash at the outer edge would narrow the pupil there
        assertEquals(VillagerEye.WHITE_HALF_WIDTH, PupilEdges.of(outward, VillagerEye.LEFT).maxX(), TOLERANCE);
        assertEquals(-VillagerEye.WHITE_HALF_WIDTH, PupilEdges.of(outward, VillagerEye.RIGHT).minX(), TOLERANCE);
        assertEquals(1.0F, outward.get(LEFT_SCALE, X), 0.0F);
        assertEquals(1.0F, outward.get(RIGHT_SCALE, X), 0.0F);
    }

    @Test
    void pupilPushedInward_squashesOnTheWhitesInnerEdge_whileItsOuterEdgeFollowsThePush_inEachEye() {
        // Arrange: each pupil, one narrowed by an expression, pushed past its eye's inner edge by a quarter of the
        // most it may squash; the inner edge is negative x for the left eye and positive x for the right
        float leftWidth = VillagerEye.PUPIL_REST_SIZE * NARROWED_SCALE;
        float rightWidth = VillagerEye.PUPIL_REST_SIZE;
        float leftOvershoot = 0.25F * (1.0F - PupilContainment.MIN_SQUASHED_SHARE) * leftWidth;
        float rightOvershoot = 0.25F * (1.0F - PupilContainment.MIN_SQUASHED_SHARE) * rightWidth;
        float pushedLeftOuterX = VillagerEye.LEFT.innerEdgeX() + leftWidth - leftOvershoot;
        float pushedRightOuterX = VillagerEye.RIGHT.innerEdgeX() - rightWidth + rightOvershoot;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_SCALE, X, NARROWED_SCALE);
        pose.set(LEFT_TRANSLATION, X, pushedLeftOuterX - leftWidth / 2.0F - VillagerEye.LEFT.pupilRestX());
        pose.set(RIGHT_TRANSLATION, X, pushedRightOuterX + rightWidth / 2.0F - VillagerEye.RIGHT.pupilRestX());

        // Act
        PupilContainment.apply(pose);

        // Assert: a plain stop would keep each pupil's width and hold its outer edge back from the push; a squash
        // about its center, or one mirrored for the wrong eye, would move its inner edge off the white's
        PupilEdges left = PupilEdges.of(pose, VillagerEye.LEFT);
        PupilEdges right = PupilEdges.of(pose, VillagerEye.RIGHT);
        assertEquals(VillagerEye.LEFT.innerEdgeX(), left.minX(), TOLERANCE);
        assertEquals(pushedLeftOuterX, left.maxX(), TOLERANCE);
        assertEquals(VillagerEye.RIGHT.innerEdgeX(), right.maxX(), TOLERANCE);
        assertEquals(pushedRightOuterX, right.minX(), TOLERANCE);
    }

    @Test
    void pupilPushedFarInward_squashesNoFurtherThanItsMinimumWidth() {
        // Arrange
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_SCALE, X, NARROWED_SCALE);
        pose.set(LEFT_TRANSLATION, X, -FAR_PUSH);
        pose.set(RIGHT_TRANSLATION, X, FAR_PUSH);

        // Act
        PupilContainment.apply(pose);

        // Assert: an unbounded squash would flatten the pupil to nothing; one measured on the rest size would ignore
        // the expression's own narrowing
        PupilEdges left = PupilEdges.of(pose, VillagerEye.LEFT);
        PupilEdges right = PupilEdges.of(pose, VillagerEye.RIGHT);
        assertEquals(VillagerEye.LEFT.innerEdgeX(), left.minX(), TOLERANCE);
        assertEquals(PupilContainment.MIN_SQUASHED_SHARE * VillagerEye.PUPIL_REST_SIZE * NARROWED_SCALE,
                left.maxX() - left.minX(), TOLERANCE);
        assertEquals(VillagerEye.RIGHT.innerEdgeX(), right.maxX(), TOLERANCE);
        assertEquals(PupilContainment.MIN_SQUASHED_SHARE * VillagerEye.PUPIL_REST_SIZE,
                right.maxX() - right.minX(), TOLERANCE);
    }

    @Test
    void pupilPushedSideways_reachesTheEdge_withItsResolvedWidth() {
        // Arrange: an expression narrows the left pupil, then the push carries it outward
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_SCALE, X, NARROWED_SCALE);
        pose.set(LEFT_TRANSLATION, X, FAR_PUSH);

        // Act
        PupilContainment.apply(pose);

        // Assert: bounds measured on the rest size would stop a narrowed pupil short of the edge
        PupilEdges edges = PupilEdges.of(pose, VillagerEye.LEFT);
        assertEquals(NARROWED_SCALE, pose.get(LEFT_SCALE, X), 0.0F);
        assertEquals(VillagerEye.WHITE_HALF_WIDTH, edges.maxX(), TOLERANCE);
    }

    @Test
    void pupilWiderThanTheWhite_isNarrowedToTheWhitesWidth() {
        // Arrange: both pupils twice as wide as the white, the left pushed against its outer edge and the right far
        // past its inner edge
        float twiceTheWhite = 4.0F * VillagerEye.WHITE_HALF_WIDTH / VillagerEye.PUPIL_REST_SIZE;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_SCALE, X, twiceTheWhite);
        pose.set(LEFT_TRANSLATION, X, FAR_PUSH);
        pose.set(RIGHT_SCALE, X, twiceTheWhite);
        pose.set(RIGHT_TRANSLATION, X, FAR_PUSH);

        // Act
        PupilContainment.apply(pose);

        // Assert: bounds left inverted by an oversized pupil would spill it past both edges of the white, and a squash
        // measured on the oversized width would keep the inward pupil as wide as the white
        PupilEdges outward = PupilEdges.of(pose, VillagerEye.LEFT);
        assertEquals(-VillagerEye.WHITE_HALF_WIDTH, outward.minX(), TOLERANCE);
        assertEquals(VillagerEye.WHITE_HALF_WIDTH, outward.maxX(), TOLERANCE);
        PupilEdges inward = PupilEdges.of(pose, VillagerEye.RIGHT);
        assertEquals(VillagerEye.RIGHT.innerEdgeX(), inward.maxX(), TOLERANCE);
        assertEquals(PupilContainment.MIN_SQUASHED_SHARE * 2.0F * VillagerEye.WHITE_HALF_WIDTH,
                inward.maxX() - inward.minX(), TOLERANCE);
    }

    @Test
    void pupilPushedUp_slidesUnderTheEyelid_byHalfItsHeightAtMost() {
        // Arrange: one pupil at its full height and one squashed by an expression
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_TRANSLATION, Y, -FAR_PUSH);
        pose.set(RIGHT_TRANSLATION, Y, -FAR_PUSH);
        pose.set(RIGHT_SCALE, Y, SQUASHED_SCALE);

        // Act
        PupilContainment.apply(pose);

        // Assert: half of each pupil, whatever its height, rises past the white's top edge, and nothing squashes it
        for (VillagerEye eye : VillagerEye.values()) {
            PupilEdges edges = PupilEdges.of(pose, eye);
            assertEquals(-VillagerEye.WHITE_HALF_HEIGHT, edges.centerY(), TOLERANCE, eye.name());
        }
        assertEquals(1.0F, pose.get(LEFT_SCALE, Y), 0.0F);
        assertEquals(SQUASHED_SCALE, pose.get(RIGHT_SCALE, Y), 0.0F);
    }

    @Test
    void pupilPushedDown_squashesOnTheWhitesBottomEdge_whileItsTopEdgeFollowsThePush() {
        // Arrange: a squashed pupil pushed past the white's bottom by a quarter of the most it may squash
        float height = VillagerEye.PUPIL_REST_SIZE * SQUASHED_SCALE;
        float overshoot = 0.25F * (1.0F - PupilContainment.MIN_SQUASHED_SHARE) * height;
        float pushedTopY = VillagerEye.WHITE_HALF_HEIGHT + overshoot - height;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_SCALE, Y, SQUASHED_SCALE);
        pose.set(LEFT_TRANSLATION, Y, pushedTopY + height / 2.0F - VillagerEye.PUPIL_REST_Y);

        // Act
        PupilContainment.apply(pose);

        // Assert: a squash without moving the pupil would leave its bottom past the white, and a squash about its
        // center would raise its top edge and show less white above
        PupilEdges edges = PupilEdges.of(pose, VillagerEye.LEFT);
        assertEquals(VillagerEye.WHITE_HALF_HEIGHT, edges.bottomY(), TOLERANCE);
        assertEquals(pushedTopY, edges.topY(), TOLERANCE);
    }

    @Test
    void pupilPushedFarDown_squashesNoFurtherThanItsMinimumHeight() {
        // Arrange
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(LEFT_SCALE, Y, SQUASHED_SCALE);
        pose.set(LEFT_TRANSLATION, Y, FAR_PUSH);

        // Act
        PupilContainment.apply(pose);

        // Assert: an unbounded squash would flatten the pupil to nothing; one measured on the rest size would ignore
        // the expression's own squash
        PupilEdges edges = PupilEdges.of(pose, VillagerEye.LEFT);
        assertEquals(VillagerEye.WHITE_HALF_HEIGHT, edges.bottomY(), TOLERANCE);
        assertEquals(PupilContainment.MIN_SQUASHED_SHARE * VillagerEye.PUPIL_REST_SIZE * SQUASHED_SCALE,
                edges.height(), TOLERANCE);
    }

    @Test
    void bounds_stayTheSame_underTheEyeballsOwnScaleAndTranslation() {
        // Arrange: the same push on two pupils, one in a raised and stretched eyeball, as a wide-eyed expression keys
        AnimationPose plain = new AnimationPose(RIG);
        AnimationPose wideEyed = new AnimationPose(RIG);
        wideEyed.set(LEFT_EYEBALL_TRANSLATION, 0.0F, -0.4F, 0.0F);
        wideEyed.set(LEFT_EYEBALL_SCALE, 1.0F, 2.5F, 1.0F);
        for (AnimationPose pose : new AnimationPose[]{plain, wideEyed}) {
            pose.set(LEFT_TRANSLATION, FAR_PUSH, FAR_PUSH, 0.0F);
        }

        // Act
        PupilContainment.apply(plain);
        PupilContainment.apply(wideEyed);

        // Assert: the eyeball moves the white and the pupil together, so bounds in head units would misplace the pupil
        for (int channel : new int[]{LEFT_TRANSLATION, LEFT_SCALE}) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                assertEquals(plain.get(channel, axis), wideEyed.get(channel, axis), 0.0F);
            }
        }
    }

    @Test
    void everyProductionClipsPupils_composedWithRest_areInsideTheBounds() throws Exception {
        // Arrange
        AnimationPose sample = new AnimationPose(RIG);
        int clipsMovingThePupils = 0;

        for (KeyframeAnimation clip : ProductionAnimationClips.all()) {
            if (ProductionAnimationClips.rigFor(clip) != RIG) {
                continue;
            }
            BakedAnimation baked = BakedAnimation.bake(clip, RIG);
            boolean moves = false;
            for (int step = 0; step <= clip.getDurationTicks() * SWEEP_SAMPLES_PER_TICK; step++) {
                float elapsedTicks = (float) step / SWEEP_SAMPLES_PER_TICK;

                // Act
                sample.resetToRest();
                baked.sampleInto(sample, elapsedTicks);

                // Assert: an authored pupil past a bound would be moved or squashed in game, not drawn as authored
                for (VillagerEye eye : VillagerEye.values()) {
                    PupilEdges edges = PupilEdges.of(sample, eye);
                    String where = clip.getId() + " " + eye + " pupil at tick " + elapsedTicks + ": " + edges;
                    assertTrue(edges.minX() >= -VillagerEye.WHITE_HALF_WIDTH - TOLERANCE
                            && edges.maxX() <= VillagerEye.WHITE_HALF_WIDTH + TOLERANCE, where);
                    assertTrue(edges.bottomY() <= VillagerEye.WHITE_HALF_HEIGHT + TOLERANCE, where);
                    assertTrue(edges.centerY() >= -VillagerEye.WHITE_HALF_HEIGHT - TOLERANCE, where);
                }
                moves |= !atRest(sample);
            }
            clipsMovingThePupils += moves ? 1 : 0;
        }
        assertTrue(clipsMovingThePupils > 0, "Discovery found no clip moving the pupils");
    }

    private static boolean atRest(AnimationPose pose) {
        for (int axis = X; axis <= Y; axis++) {
            if (pose.get(LEFT_TRANSLATION, axis) != 0.0F || pose.get(RIGHT_TRANSLATION, axis) != 0.0F
                    || pose.get(LEFT_SCALE, axis) != 1.0F || pose.get(RIGHT_SCALE, axis) != 1.0F) {
                return false;
            }
        }
        return true;
    }

    private static AnimationPose copyOf(AnimationPose pose) {
        AnimationPose copy = new AnimationPose(RIG);
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            copy.set(channel, pose.get(channel, X), pose.get(channel, Y), pose.get(channel, Z));
        }
        return copy;
    }

}
