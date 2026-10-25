package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadTrailTest {

    private static final float TOLERANCE = 1.0E-5F;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

    private static final long AGE_TICKS = 1_000L;
    private static final float LOOK_YAW_DEGREES = 30.0F;
    private static final float OTHER_LOOK_YAW_DEGREES = -45.0F;
    private static final float LOOK_PITCH_DEGREES = 20.0F;
    private static final float BODY_HEADING_DEGREES = 123.0F;

    @Test
    void displayedLook_isTheObservedLook_onceSettled() {
        // Arrange
        HeadTrail trail = new HeadTrail();
        FollowerStep step = new FollowerStep();
        VillagerAnimationObservation observation = looking(AGE_TICKS, LOOK_YAW_DEGREES)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .build();

        // Act
        follow(trail, step, observation);

        // Assert: a yaw left in world heading would show the body heading as well
        assertEquals(LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, trail.displayedYawRadians(observation), TOLERANCE);
        assertEquals(LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS, trail.displayedPitchRadians(observation), TOLERANCE);
    }

    @Test
    void displayedLook_trailsANewLook_partWayAcrossOneSample() {
        // Arrange
        HeadTrail trail = new HeadTrail();
        FollowerStep step = new FollowerStep();
        follow(trail, step, looking(AGE_TICKS, LOOK_YAW_DEGREES).build());
        VillagerAnimationObservation turned = looking(AGE_TICKS + 1, OTHER_LOOK_YAW_DEGREES)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .build();

        // Act
        follow(trail, step, turned);

        // Assert: neither jumping to the new look nor holding the old one
        float yaw = trail.displayedYawRadians(turned);
        float pitch = trail.displayedPitchRadians(turned);
        assertTrue(yaw < LOOK_YAW_DEGREES * DEGREES_TO_RADIANS - TOLERANCE, "the yaw held the old look");
        assertTrue(yaw > OTHER_LOOK_YAW_DEGREES * DEGREES_TO_RADIANS + TOLERANCE, "the yaw jumped to the new look");
        assertTrue(pitch > TOLERANCE, "the pitch held the old look");
        assertTrue(pitch < LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS - TOLERANCE, "the pitch jumped to the new look");
    }

    @Test
    void displayedYaw_keepsTheHeadOnAFixedLookTarget_whileTheBodyTurnsUnderIt() {
        // Arrange: counter-rotate the body-relative look to keep the target's world heading fixed
        HeadTrail trail = new HeadTrail();
        FollowerStep step = new FollowerStep();
        float targetHeadingDegrees = BODY_HEADING_DEGREES + LOOK_YAW_DEGREES;
        follow(trail, step, looking(AGE_TICKS, LOOK_YAW_DEGREES).bodyHeadingDegrees(BODY_HEADING_DEGREES).build());

        for (int tick = 1; tick <= 10; tick++) {
            float bodyHeading = BODY_HEADING_DEGREES + 6.0F * tick;
            VillagerAnimationObservation observation = looking(AGE_TICKS + tick, targetHeadingDegrees - bodyHeading)
                    .bodyHeadingDegrees(bodyHeading)
                    .build();

            // Act
            follow(trail, step, observation);

            // Assert: a trail kept relative to the body would drag the head along with it
            assertEquals((targetHeadingDegrees - bodyHeading) * DEGREES_TO_RADIANS,
                    trail.displayedYawRadians(observation), TOLERANCE, "tick " + tick);
        }
    }

    @Test
    void displayedYaw_isClampedToTheWidestDisplayedYaw_eitherSide() {
        // Arrange: a look held still in world heading while the body swings far past it in one sample
        HeadTrail trail = new HeadTrail();
        FollowerStep step = new FollowerStep();
        follow(trail, step, looking(AGE_TICKS, 0.0F).build());
        float pastTheClampDegrees = HeadTrail.MAX_DISPLAYED_YAW_RADIANS / DEGREES_TO_RADIANS + 10.0F;
        VillagerAnimationObservation bodySwungLeft = looking(AGE_TICKS + 1, pastTheClampDegrees)
                .bodyHeadingDegrees(-pastTheClampDegrees)
                .build();
        VillagerAnimationObservation bodySwungRight = looking(AGE_TICKS + 1, -pastTheClampDegrees)
                .bodyHeadingDegrees(pastTheClampDegrees)
                .build();

        // Act
        follow(trail, step, bodySwungLeft);
        float leftYaw = trail.displayedYawRadians(bodySwungLeft);
        float rightYaw = trail.displayedYawRadians(bodySwungRight);

        // Assert
        assertEquals(HeadTrail.MAX_DISPLAYED_YAW_RADIANS, leftYaw, TOLERANCE);
        assertEquals(-HeadTrail.MAX_DISPLAYED_YAW_RADIANS, rightYaw, TOLERANCE);
    }

    @Test
    void trail_jumpsOntoTheLook_pastATeleportSizedTurn_andTrailsOneShortOfIt() {
        // Arrange
        HeadTrail jumping = new HeadTrail();
        HeadTrail trailing = new HeadTrail();
        FollowerStep jumpingStep = new FollowerStep();
        FollowerStep trailingStep = new FollowerStep();
        follow(jumping, jumpingStep, looking(AGE_TICKS, 0.0F).build());
        follow(trailing, trailingStep, looking(AGE_TICKS, 0.0F).build());
        float teleportTurnDegrees = ExponentialFollower.TELEPORT_TURN_RADIANS / DEGREES_TO_RADIANS;
        // The body turns with the look, so the jump stays inside the displayed yaw's clamp
        VillagerAnimationObservation pastTheTurn = looking(AGE_TICKS + 1, 10.0F)
                .bodyHeadingDegrees(teleportTurnDegrees - 5.0F)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .build();
        VillagerAnimationObservation shortOfTheTurn = looking(AGE_TICKS + 1, 10.0F)
                .bodyHeadingDegrees(teleportTurnDegrees - 15.0F)
                .headPitchDegrees(LOOK_PITCH_DEGREES)
                .build();

        // Act
        follow(jumping, jumpingStep, pastTheTurn);
        follow(trailing, trailingStep, shortOfTheTurn);

        // Assert: the whole head jumps, pitch included
        assertEquals(10.0F * DEGREES_TO_RADIANS, jumping.displayedYawRadians(pastTheTurn), TOLERANCE);
        assertEquals(LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS, jumping.displayedPitchRadians(pastTheTurn), TOLERANCE);
        assertTrue(Math.abs(trailing.displayedYawRadians(shortOfTheTurn) - 10.0F * DEGREES_TO_RADIANS) > TOLERANCE,
                "a turn short of a teleport jumped");
    }

    @Test
    void upsideDown_displaysTheObservedLook_andTrailsAsTheMirrorOfAnUprightVillager() {
        // Arrange: two villagers with one world look history; vanilla negates the upside-down one's look
        HeadTrail upright = new HeadTrail();
        HeadTrail upsideDown = new HeadTrail();
        FollowerStep uprightStep = new FollowerStep();
        FollowerStep upsideDownStep = new FollowerStep();
        VillagerAnimationObservation uprightFirst = looking(AGE_TICKS, LOOK_YAW_DEGREES)
                .headPitchDegrees(LOOK_PITCH_DEGREES).build();
        VillagerAnimationObservation upsideDownFirst = looking(AGE_TICKS, -LOOK_YAW_DEGREES)
                .headPitchDegrees(-LOOK_PITCH_DEGREES).upsideDown(true).build();
        follow(upright, uprightStep, uprightFirst);
        follow(upsideDown, upsideDownStep, upsideDownFirst);
        float settledYaw = upsideDown.displayedYawRadians(upsideDownFirst);
        float settledPitch = upsideDown.displayedPitchRadians(upsideDownFirst);
        VillagerAnimationObservation uprightTurned = looking(AGE_TICKS + 1, OTHER_LOOK_YAW_DEGREES).build();
        VillagerAnimationObservation upsideDownTurned = looking(AGE_TICKS + 1, -OTHER_LOOK_YAW_DEGREES)
                .upsideDown(true).build();

        // Act
        follow(upright, uprightStep, uprightTurned);
        follow(upsideDown, upsideDownStep, upsideDownTurned);

        // Assert: retaining the upside-down sign flip in world heading would reverse the trailed turn
        assertEquals(-LOOK_YAW_DEGREES * DEGREES_TO_RADIANS, settledYaw, TOLERANCE);
        assertEquals(-LOOK_PITCH_DEGREES * DEGREES_TO_RADIANS, settledPitch, TOLERANCE);
        assertEquals(-upright.displayedYawRadians(uprightTurned), upsideDown.displayedYawRadians(upsideDownTurned),
                TOLERANCE);
        assertEquals(-upright.displayedPitchRadians(uprightTurned),
                upsideDown.displayedPitchRadians(upsideDownTurned), TOLERANCE);
    }

    private static void follow(HeadTrail trail, FollowerStep step, VillagerAnimationObservation observation) {
        step.measure(observation.getAgeTicks(), observation.getPartialTicks(), false);
        trail.follow(observation, step);
    }

    private static VillagerAnimationObservations.ObservationBuilder looking(long age, float netHeadYawDegrees) {
        return VillagerAnimationObservations.observation()
                .ageTicks(age)
                .netHeadYawDegrees(netHeadYawDegrees)
                .navigationType(NavigationType.STROLL)
                .motionArchetype(AnimationArchetype.NONE)
                .selectionContext(AnimationSelectionContext.generic());
    }

}
