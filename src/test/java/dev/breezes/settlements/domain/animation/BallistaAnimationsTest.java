package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallistaAnimationsTest {

    private static final AnimationRig RIG = BallistaAnimationTargets.RIG;

    private static final BakedAnimation WIND = BakedAnimation.bake(BallistaAnimations.wind(), RIG);
    private static final BakedAnimation FIRE = BakedAnimation.bake(BallistaAnimations.fire(), RIG);

    // Loose enough for a value interpolated onto a keyframe to match that keyframe, far tighter than anything visible
    private static final float TOLERANCE = 1.0E-4F;

    @Test
    void fire_startsOnThePoseWindEndsOn_onEveryChannel() {
        // Arrange: a channel only one clip keys reads as rest in the other, which is what the machine draws for that
        // bone once the clip that does not key it takes over
        AnimationPose windEnd = sampled(WIND, WIND.getDurationTicks());

        // Act
        AnimationPose fireStart = sampled(FIRE, 0.0F);

        // Assert: any difference is a snap on the tick a cocked machine fires
        assertSamePose(windEnd, fireStart, "fire's first frame against wind's last");
    }

    @Test
    void wind_startsOnTheRestPose() {
        // Act
        AnimationPose windStart = sampled(WIND, 0.0F);

        // Assert: an unwound machine holds the rest pose, so any offset here is a snap on the tick a wind starts
        assertSamePose(new AnimationPose(RIG), windStart, "wind's first frame against rest");
    }

    @Test
    void fire_endsOnTheRestPose() {
        // Act
        AnimationPose fireEnd = sampled(FIRE, FIRE.getDurationTicks());

        // Assert: a fired machine holds the rest pose, so any offset here is a snap on the tick the clip runs out
        assertSamePose(new AnimationPose(RIG), fireEnd, "fire's last frame against rest");
    }

    @Test
    void fire_pusherReachesRest_onTheReleaseTickAndNoSooner() {
        // Act, Assert: a release tick before the pusher arrives launches the bolt while the seated one is still drawn
        // travelling forward, and one after it leaves the seated bolt parked at rest
        for (int tick = 0; tick < BallistaAnimations.FIRE_RELEASE_TICK; tick++) {
            assertFalse(pusherAtRest(sampled(FIRE, tick)), "pusher already at rest on tick " + tick);
        }
        assertTrue(pusherAtRest(sampled(FIRE, BallistaAnimations.FIRE_RELEASE_TICK)),
                "pusher not at rest on the release tick");
    }

    private static AnimationPose sampled(BakedAnimation clip, float elapsedTicks) {
        AnimationPose pose = new AnimationPose(RIG);
        clip.sampleInto(pose, elapsedTicks);
        return pose;
    }

    private static boolean pusherAtRest(AnimationPose pose) {
        int pusher = RIG.indexOf(BallistaAnimationTargets.PUSHER_TRANSLATION);
        for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
            if (Math.abs(pose.get(pusher, axis)) > TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static void assertSamePose(AnimationPose expected, AnimationPose actual, String context) {
        for (int channel = 0; channel < RIG.getChannelCount(); channel++) {
            for (int axis = 0; axis < AnimationPose.COMPONENTS; axis++) {
                assertEquals(expected.get(channel, axis), actual.get(channel, axis), TOLERANCE,
                        context + ": " + RIG.channel(channel).getId() + " axis " + axis);
            }
        }
    }

}
