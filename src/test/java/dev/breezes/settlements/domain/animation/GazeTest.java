package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GazeTest {

    private static final float TOLERANCE = 1.0E-6F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int ROOT = RIG.indexOf(AnimationTargets.ROOT_ROTATION);
    private static final int TORSO = RIG.indexOf(AnimationTargets.BODY_ROTATION);
    private static final int HEAD = RIG.indexOf(AnimationTargets.HEAD_ROTATION);
    private static final int LEFT_PUPIL = RIG.indexOf(AnimationTargets.PUPIL_LEFT_TRANSLATION);
    private static final int RIGHT_PUPIL = RIG.indexOf(AnimationTargets.PUPIL_RIGHT_TRANSLATION);
    private static final int LEFT_PUPIL_SCALE = RIG.indexOf(AnimationTargets.PUPIL_LEFT_SCALE);

    // A small look, so no source's share of it comes near a pupil bound
    private static final float LOOK_DEGREES = 10.0F;
    // A wide look, where an angle and the direction's component part ways
    private static final float WIDE_LOOK_DEGREES = 60.0F;

    private enum BoneTurningTheHead {
        ROOT_BONE(ROOT),
        TORSO_BONE(TORSO),
        HEAD_BONE(HEAD);

        private final int rotationChannel;

        BoneTurningTheHead(int rotationChannel) {
            this.rotationChannel = rotationChannel;
        }
    }

    @ParameterizedTest
    @CsvSource({"10, 0", "-10, 0", "0, 10", "0, -10", "10, -10"})
    void headTurn_movesBothPupilsTheSameWayItTurns_whileTheHeadIsDrawnOnTheLook(float yawDegrees,
                                                                                  float pitchDegrees) {
        // Arrange: the drawn head turned, with the look on it, so the look residual is zero and only the drawn head's
        // facing moves the pupils
        AnimationPose drawn = headTurnedTo(yawDegrees, pitchDegrees);

        // Act
        AnimationPose pupils = gazeOnRest(drawn, looking(yawDegrees, pitchDegrees));

        // Assert: a head facing right is toward negative x and one facing down toward positive y; mirroring the
        // offset per eye, or dropping the source, breaks either the signs or the agreement between the eyes
        assertMovesWithSign(-Math.signum(yawDegrees), pupils.get(LEFT_PUPIL, X), "horizontal");
        assertMovesWithSign(Math.signum(pitchDegrees), pupils.get(LEFT_PUPIL, Y), "vertical");
        assertBothPupilsMoveAlike(pupils);
    }

    @ParameterizedTest
    @EnumSource(BoneTurningTheHead.class)
    void drawnHeadFacing_movesThePupils_whicheverBoneTurnsTheHead(BoneTurningTheHead turningBone) {
        // Arrange: one bone on the chain turns the drawn head right, and the look lies on it
        AnimationPose drawn = new AnimationPose(RIG);
        drawn.set(turningBone.rotationChannel, Y, LOOK_DEGREES * DEGREES_TO_RADIANS);

        // Act
        AnimationPose pupils = gazeOnRest(drawn, looking(LOOK_DEGREES, 0.0F));

        // Assert: a share read from the head channel alone would leave the pupils still under a torso or root turn
        assertTrue(pupils.get(LEFT_PUPIL, X) < -TOLERANCE, "the pupils did not move the way the head faces");
        assertEquals(0.0F, pupils.get(LEFT_PUPIL, Y), TOLERANCE);
        assertBothPupilsMoveAlike(pupils);
    }

    @ParameterizedTest
    @EnumSource(value = BoneTurningTheHead.class, names = {"ROOT_BONE", "TORSO_BONE"})
    void turnBelowTheHead_underALookStraightAhead_movesThePupilsBackTowardTheLook(BoneTurningTheHead turningBone) {
        // Arrange: a bone the head hangs from turns it right, with the look straight ahead of the body and, for
        // comparison, on the drawn head
        AnimationPose drawn = new AnimationPose(RIG);
        drawn.set(turningBone.rotationChannel, Y, LOOK_DEGREES * DEGREES_TO_RADIANS);

        // Act
        AnimationPose lookAhead = gazeOnRest(drawn, looking(0.0F, 0.0F));
        AnimationPose lookOnTheHead = gazeOnRest(drawn, looking(LOOK_DEGREES, 0.0F));

        // Assert: the look ahead lies to the drawn head's left, toward positive x; a missing or flipped residual
        // would leave the pupils no further left than with the look on the head
        assertTrue(lookAhead.get(LEFT_PUPIL, X) > lookOnTheHead.get(LEFT_PUPIL, X) + TOLERANCE,
                "the pupils did not move toward the look");
        assertEquals(0.0F, lookAhead.get(LEFT_PUPIL, Y), TOLERANCE);
        assertBothPupilsMoveAlike(lookAhead);
    }

    @Test
    void torsoLean_underALevelLook_raisesThePupilsTowardTheLook() {
        // Arrange: the torso leans the head forward, with the look level and, for comparison, on the drawn head
        AnimationPose drawn = new AnimationPose(RIG);
        drawn.set(TORSO, X, LOOK_DEGREES * DEGREES_TO_RADIANS);

        // Act
        AnimationPose levelLook = gazeOnRest(drawn, looking(0.0F, 0.0F));
        AnimationPose lookOnTheHead = gazeOnRest(drawn, looking(0.0F, LOOK_DEGREES));

        // Assert: the level look lies above the leaning head, toward negative y
        assertTrue(levelLook.get(LEFT_PUPIL, Y) < lookOnTheHead.get(LEFT_PUPIL, Y) - TOLERANCE,
                "the pupils did not rise toward the look");
        assertEquals(0.0F, levelLook.get(LEFT_PUPIL, X), TOLERANCE);
        assertBothPupilsMoveAlike(levelLook);
    }

    @Test
    void authoredHold_keepingTheHeadAhead_movesThePupilsByTheResidualAlone() {
        // Arrange: a head held straight ahead under a small and a wide look, sideways and then up
        AnimationPose held = new AnimationPose(RIG);
        VillagerAnimationObservation[] sideways = {looking(LOOK_DEGREES, 0.0F), looking(WIDE_LOOK_DEGREES, 0.0F)};
        VillagerAnimationObservation[] upward = {looking(0.0F, -LOOK_DEGREES), looking(0.0F, -WIDE_LOOK_DEGREES)};
        float[] horizontalPerResidual = new float[2];
        float[] verticalPerResidual = new float[2];

        // Act
        for (int look = 0; look < 2; look++) {
            horizontalPerResidual[look] = gazeOnRest(held, sideways[look]).get(LEFT_PUPIL, X)
                    / composed(held, sideways[look]).lookResidualRightward();
            verticalPerResidual[look] = gazeOnRest(held, upward[look]).get(LEFT_PUPIL, Y)
                    / composed(held, upward[look]).lookResidualDownward();
        }

        // Assert: the eyes land on the look, so the offset is a fixed multiple of the residual at any width of look; a
        // share of the observed look angle on top would grow faster than the residual at the wide look
        assertEquals(horizontalPerResidual[0], horizontalPerResidual[1], 1.0E-4F * Math.abs(horizontalPerResidual[0]));
        assertEquals(verticalPerResidual[0], verticalPerResidual[1], 1.0E-4F * Math.abs(verticalPerResidual[0]));
        assertTrue(horizontalPerResidual[0] < 0.0F, "a look to the right did not move the pupils right");
        assertTrue(verticalPerResidual[0] > 0.0F, "a look up did not raise the pupils");
    }

    @Test
    void drawnHeadFacingThroughStraightDown_keepsTheGazeFiniteAndContinuous() {
        // Arrange: a head turned aside and pitched through straight down, where its heading is undefined, with the look
        // on it
        float yawDegrees = 30.0F;
        float justShortDegrees = 89.99F;
        float justPastDegrees = 90.01F;

        // Act
        AnimationPose shortOfDown = gazeOnRest(headTurnedTo(yawDegrees, justShortDegrees),
                looking(yawDegrees, justShortDegrees));
        AnimationPose straightDown = gazeOnRest(headTurnedTo(yawDegrees, 90.0F), looking(yawDegrees, 90.0F));
        AnimationPose pastDown = gazeOnRest(headTurnedTo(yawDegrees, justPastDegrees),
                looking(yawDegrees, justPastDegrees));

        // Assert: a share taken from the head's heading would swing half a turn as the pitch crosses straight down
        for (int axis = X; axis <= Y; axis++) {
            assertTrue(Float.isFinite(straightDown.get(LEFT_PUPIL, axis)), "a non-finite offset on axis " + axis);
            assertEquals(shortOfDown.get(LEFT_PUPIL, axis), pastDown.get(LEFT_PUPIL, axis), 1.0E-3F,
                    "the offset jumped across straight down on axis " + axis);
        }
    }

    @Test
    void upsideDown_mirrorsTheGazeInModelSpace_forTheSameWorldLook() {
        // Arrange: one world look, upright and upside down, where vanilla negates the look it reports and the model
        // is turned half a turn about its forward axis; once with the head drawn on the look and once held ahead
        VillagerAnimationObservation upright = looking(LOOK_DEGREES, LOOK_DEGREES);
        VillagerAnimationObservation upsideDown = VillagerAnimationObservations.observation()
                .netHeadYawDegrees(-LOOK_DEGREES)
                .headPitchDegrees(-LOOK_DEGREES)
                .upsideDown(true)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic())
                .build();

        // Act
        AnimationPose uprightOnTheLook = gazeOnRest(headTurnedTo(LOOK_DEGREES, LOOK_DEGREES), upright);
        AnimationPose upsideDownOnTheLook = gazeOnRest(headTurnedTo(-LOOK_DEGREES, -LOOK_DEGREES), upsideDown);
        AnimationPose uprightHeld = gazeOnRest(new AnimationPose(RIG), upright);
        AnimationPose upsideDownHeld = gazeOnRest(new AnimationPose(RIG), upsideDown);

        // Assert: the half turn maps a negated model offset onto the same world offset; flipping either source again
        // would leave the offsets equal instead
        for (int axis = X; axis <= Y; axis++) {
            assertTrue(Math.abs(uprightOnTheLook.get(LEFT_PUPIL, axis)) > TOLERANCE, "no offset on axis " + axis);
            assertEquals(-uprightOnTheLook.get(LEFT_PUPIL, axis), upsideDownOnTheLook.get(LEFT_PUPIL, axis), TOLERANCE);
            assertEquals(-uprightHeld.get(LEFT_PUPIL, axis), upsideDownHeld.get(LEFT_PUPIL, axis), TOLERANCE);
        }
    }

    @Test
    void weight_scalesTheGazeAddedOntoAuthoredPupils_andZeroOrNaNAddsNothing() {
        // Arrange: authored pupils, and a held head under a look up and to the right
        AnimationPose drawn = new AnimationPose(RIG);
        VillagerAnimationObservation look = looking(LOOK_DEGREES, -LOOK_DEGREES);
        AnimationPose full = authoredPupils();
        AnimationPose half = authoredPupils();
        AnimationPose zero = authoredPupils();
        AnimationPose notANumber = authoredPupils();
        AnimationPose authored = authoredPupils();

        // Act
        apply(full, drawn, look, 1.0F);
        apply(half, drawn, look, 0.5F);
        apply(zero, drawn, look, 0.0F);
        apply(notANumber, drawn, look, Float.NaN);

        // Assert: a gaze owning the pupils would drop the authored offset; one ignoring its weight would move them all
        for (int pupil : new int[]{LEFT_PUPIL, RIGHT_PUPIL}) {
            for (int axis = X; axis <= Y; axis++) {
                float fullOffset = full.get(pupil, axis) - authored.get(pupil, axis);
                assertTrue(Math.abs(fullOffset) > TOLERANCE, "no gaze on axis " + axis);
                assertEquals(0.5F * fullOffset, half.get(pupil, axis) - authored.get(pupil, axis), TOLERANCE);
                assertEquals(authored.get(pupil, axis), zero.get(pupil, axis), 0.0F);
                assertEquals(authored.get(pupil, axis), notANumber.get(pupil, axis), 0.0F);
            }
            assertEquals(authored.get(pupil, Z), full.get(pupil, Z), 0.0F, "depth");
        }
        for (int axis = X; axis <= Z; axis++) {
            assertEquals(authored.get(LEFT_PUPIL_SCALE, axis), full.get(LEFT_PUPIL_SCALE, axis), 0.0F, "scale");
        }
    }

    private static AnimationPose gazeOnRest(AnimationPose drawn, VillagerAnimationObservation observation) {
        AnimationPose pose = new AnimationPose(RIG);
        apply(pose, drawn, observation, 1.0F);
        return pose;
    }

    private static void apply(AnimationPose pose,
                              AnimationPose drawn,
                              VillagerAnimationObservation observation,
                              float weight) {
        Gaze.apply(pose, weight, composed(drawn, observation));
    }

    private static HeadFrame composed(AnimationPose drawn, VillagerAnimationObservation observation) {
        HeadFrame headFrame = new HeadFrame();
        headFrame.compose(drawn, observation);
        return headFrame;
    }

    private static AnimationPose headTurnedTo(float yawDegrees, float pitchDegrees) {
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(HEAD, X, pitchDegrees * DEGREES_TO_RADIANS);
        pose.set(HEAD, Y, yawDegrees * DEGREES_TO_RADIANS);
        return pose;
    }

    private static AnimationPose authoredPupils() {
        AnimationPose pose = new AnimationPose(RIG);
        for (int pupil : new int[]{LEFT_PUPIL, RIGHT_PUPIL}) {
            pose.set(pupil, X, 0.1F);
            pose.set(pupil, Y, -0.1F);
            pose.set(pupil, Z, 0.2F);
        }
        pose.set(LEFT_PUPIL_SCALE, Y, 0.8F);
        return pose;
    }

    /**
     * Asserts the offset lies clear of zero on the side the sign gives, or at zero for a zero sign.
     */
    private static void assertMovesWithSign(float expectedSign, float offset, String axis) {
        if (expectedSign == 0.0F) {
            assertEquals(0.0F, offset, TOLERANCE, axis + " offset without a turn on that axis");
        } else {
            assertTrue(expectedSign * offset > TOLERANCE, axis + " offset on the wrong side: " + offset);
        }
    }

    private static void assertBothPupilsMoveAlike(AnimationPose pose) {
        assertEquals(pose.get(LEFT_PUPIL, X), pose.get(RIGHT_PUPIL, X), 0.0F, "horizontal offsets differ per eye");
        assertEquals(pose.get(LEFT_PUPIL, Y), pose.get(RIGHT_PUPIL, Y), 0.0F, "vertical offsets differ per eye");
    }

    private static VillagerAnimationObservation looking(float netHeadYawDegrees, float headPitchDegrees) {
        return VillagerAnimationObservations.observation()
                .netHeadYawDegrees(netHeadYawDegrees)
                .headPitchDegrees(headPitchDegrees)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic())
                .build();
    }

}
