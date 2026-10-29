package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadFrameTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;
    private static final int X = 0;
    private static final int Y = 1;

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;
    private static final int ROOT = RIG.indexOf(AnimationTargets.ROOT_ROTATION);
    private static final int TORSO = RIG.indexOf(AnimationTargets.BODY_ROTATION);
    private static final int HEAD = RIG.indexOf(AnimationTargets.HEAD_ROTATION);

    private static final float LOOK_YAW_RADIANS = 30.0F * DEGREES_TO_RADIANS;

    private static final VillagerAnimationObservation LEVEL_LOOK = looking(0.0F, 0.0F);

    private enum BoneBelowTheHead {
        ROOT_BONE(ROOT),
        TORSO_BONE(TORSO);

        private final int rotationChannel;

        BoneBelowTheHead(int rotationChannel) {
            this.rotationChannel = rotationChannel;
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {0.6F, -0.6F})
    void modelHeading_ofAPureHeadYaw_isThatYaw(float headYaw) {
        // Arrange: an isolated head yaw makes a sign error in the forward-vector projection observable
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(HEAD, Y, headYaw);
        HeadFrame headFrame = new HeadFrame();

        // Act
        headFrame.compose(pose, LEVEL_LOOK);

        // Assert: a heading read off the wrong side of the forward direction would come out negated
        assertEquals(headYaw, headFrame.modelHeadingRadians(), TOLERANCE);
        assertFalse(headFrame.isHeadingDegenerate());
    }

    @ParameterizedTest
    @EnumSource(BoneBelowTheHead.class)
    void modelHeading_carriesATurnBelowTheHead_underAFixedLook(BoneBelowTheHead turningBone) {
        // Arrange: a fixed look on the head, and a turn of a bone it hangs from
        float turn = 0.4F;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(HEAD, Y, LOOK_YAW_RADIANS);
        pose.set(turningBone.rotationChannel, Y, turn);
        HeadFrame headFrame = new HeadFrame();

        // Act
        headFrame.compose(pose, LEVEL_LOOK);

        // Assert: the head channel alone would read the look without the turn
        assertEquals(LOOK_YAW_RADIANS + turn, headFrame.modelHeadingRadians(), TOLERANCE);
    }

    @Test
    void modelHeading_underATorsoLeanWithTheHeadPitchedBack_isTheComposedHeading() {
        // Arrange: the torso pitches forward and the head back; the look turns the head well aside
        float lookYaw = 60.0F * DEGREES_TO_RADIANS;
        float torsoPitch = 20.0F * DEGREES_TO_RADIANS;
        float headPitch = -10.0F * DEGREES_TO_RADIANS;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(TORSO, X, torsoPitch);
        pose.set(HEAD, X, headPitch);
        pose.set(HEAD, Y, lookYaw);
        HeadFrame headFrame = new HeadFrame();
        // Derive the projected heading independently so the expectation can detect an incorrect rotation order
        float expected = (float) Math.atan2(
                Math.cos(headPitch) * Math.sin(lookYaw),
                Math.cos(headPitch) * Math.cos(lookYaw) * Math.cos(torsoPitch)
                        - Math.sin(headPitch) * Math.sin(torsoPitch));

        // Act
        headFrame.compose(pose, LEVEL_LOOK);

        // Assert: composing the bones in the wrong order, or the head's axes in the wrong order, misses the lean
        assertTrue(Math.abs(expected - lookYaw) > 100.0F * TOLERANCE, "the lean must change the heading");
        assertEquals(expected, headFrame.modelHeadingRadians(), TOLERANCE);
    }

    @Test
    void isHeadingDegenerate_onlyWhileTheHeadFacesNearlyStraightDown() {
        // Arrange: the head pitched straight down by itself, by a torso lean and a head pitch together, and steeply
        // but clear of vertical
        AnimationPose straightDown = new AnimationPose(RIG);
        straightDown.set(HEAD, X, 90.0F * DEGREES_TO_RADIANS);
        straightDown.set(HEAD, Y, LOOK_YAW_RADIANS);
        AnimationPose leanedDown = new AnimationPose(RIG);
        leanedDown.set(TORSO, X, 60.0F * DEGREES_TO_RADIANS);
        leanedDown.set(HEAD, X, 30.0F * DEGREES_TO_RADIANS);
        AnimationPose steep = new AnimationPose(RIG);
        steep.set(HEAD, X, 60.0F * DEGREES_TO_RADIANS);
        HeadFrame headFrame = new HeadFrame();

        // Act, Assert: without the check the nose would chase whatever heading rounding left
        headFrame.compose(straightDown, LEVEL_LOOK);
        assertTrue(headFrame.isHeadingDegenerate(), "straight down");
        headFrame.compose(leanedDown, LEVEL_LOOK);
        assertTrue(headFrame.isHeadingDegenerate(), "leaned down");
        headFrame.compose(steep, LEVEL_LOOK);
        assertFalse(headFrame.isHeadingDegenerate(), "steep");
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "60, 0", "-30, 45", "70, -85", "-80, 89"})
    void lookResidual_isZero_whileTheHeadIsDrawnOnTheLook(float lookYawDegrees, float lookPitchDegrees) {
        // Arrange: the head channel holds the look, as the Look slot draws a settled trail; the steep looks have no
        // stable heading, so a residual read as angles would be undefined there
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(HEAD, X, lookPitchDegrees * DEGREES_TO_RADIANS);
        pose.set(HEAD, Y, lookYawDegrees * DEGREES_TO_RADIANS);
        HeadFrame headFrame = new HeadFrame();

        // Act
        headFrame.compose(pose, looking(lookYawDegrees, lookPitchDegrees));

        // Assert: building the look in another rotation order than the head's leaves a residual at a combined look
        assertEquals(0.0F, headFrame.lookResidualRightward(), TOLERANCE);
        assertEquals(0.0F, headFrame.lookResidualDownward(), TOLERANCE);
    }

    @Test
    void lookResidual_carriesTheLook_whileAnAuthoredHoldKeepsTheHeadStraight() {
        // Arrange: a head held straight ahead, with the look up and to the right
        float lookYawDegrees = 30.0F;
        float lookPitchDegrees = -20.0F;
        HeadFrame headFrame = new HeadFrame();

        // Act
        headFrame.compose(new AnimationPose(RIG), looking(lookYawDegrees, lookPitchDegrees));

        // Assert: the look's own unit direction, so a residual ignoring the hold would read zero
        float yaw = lookYawDegrees * DEGREES_TO_RADIANS;
        float pitch = lookPitchDegrees * DEGREES_TO_RADIANS;
        assertEquals((float) (Math.sin(yaw) * Math.cos(pitch)), headFrame.lookResidualRightward(), TOLERANCE);
        assertEquals((float) Math.sin(pitch), headFrame.lookResidualDownward(), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(BoneBelowTheHead.class)
    void lookResidual_liesOppositeATurnBelowTheHead_underAFixedLook(BoneBelowTheHead turningBone) {
        // Arrange: the head drawn on the look, carried further right by a bone it hangs from
        float turn = 0.4F;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(HEAD, Y, LOOK_YAW_RADIANS);
        pose.set(turningBone.rotationChannel, Y, turn);
        HeadFrame headFrame = new HeadFrame();

        // Act
        headFrame.compose(pose, looking(LOOK_YAW_RADIANS / DEGREES_TO_RADIANS, 0.0F));

        // Assert: the look now lies to the head's left; the head channel alone would read no residual
        assertEquals((float) -Math.sin(turn), headFrame.lookResidualRightward(), TOLERANCE);
        assertEquals(0.0F, headFrame.lookResidualDownward(), TOLERANCE);
    }

    @Test
    void lookResidual_liesAboveTheHead_underATorsoLeanWithALevelLook() {
        // Arrange: a level look on the head, while the torso leans it forward
        float lean = 0.3F;
        AnimationPose pose = new AnimationPose(RIG);
        pose.set(TORSO, X, lean);
        HeadFrame headFrame = new HeadFrame();

        // Act
        headFrame.compose(pose, LEVEL_LOOK);

        // Assert: an inverted orientation would put the look below the leaning head
        assertEquals((float) -Math.sin(lean), headFrame.lookResidualDownward(), TOLERANCE);
        assertEquals(0.0F, headFrame.lookResidualRightward(), TOLERANCE);
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
