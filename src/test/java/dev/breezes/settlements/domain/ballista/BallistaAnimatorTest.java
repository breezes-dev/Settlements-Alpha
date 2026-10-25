package dev.breezes.settlements.domain.ballista;

import dev.breezes.settlements.domain.animation.AnimationPose;
import dev.breezes.settlements.domain.animation.AnimationRig;
import dev.breezes.settlements.domain.animation.BakedAnimation;
import dev.breezes.settlements.domain.animation.BallistaAnimationTargets;
import dev.breezes.settlements.domain.animation.BallistaAnimations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BallistaAnimatorTest {

    private static final AnimationRig RIG = BallistaAnimationTargets.RIG;

    private static final BakedAnimation WIND = BakedAnimation.bake(BallistaAnimations.wind(), RIG);
    private static final int WIND_LENGTH = WIND.getDurationTicks();

    private static final BakedAnimation FIRE = BakedAnimation.bake(BallistaAnimations.fire(), RIG);
    private static final int FIRE_LENGTH = FIRE.getDurationTicks();

    // Loose enough for a value interpolated onto a keyframe to match that keyframe, far tighter than anything visible
    private static final float TOLERANCE = 1.0E-5F;

    private static final long STARTED_AT = 500L;

    // Fractions of a tick a frame may be drawn at, including one just short of the next tick
    private static final float[] PARTIAL_TICKS = {0.0F, 0.25F, 0.5F, 0.999F};

    // Ticks watched either side of a clip, so the held poses before and after it are drawn too
    private static final int MARGIN_TICKS = 5;

    // Ticks a client's wind clip still has to play when the server's firing reaches it
    private static final int WIND_TICKS_LEFT_WHEN_FIRED = 3;

    @Test
    void sampleInto_followsTheWindClip_fromTheRestPoseThroughTheCockedPose_forAWindWatchedFromItsStart() {
        // Arrange
        BallistaAnimator animator = new BallistaAnimator();
        BallistaStateMachine state = BallistaStateMachine.unwound();
        AnimationPose actual = new AnimationPose(RIG);

        // Act, Assert: stepped the way a client ticks, every frame lies on the clip's own timeline, held at rest before
        // the wind and on its last frame after it. Dropping to rest as the clip runs out, or drawing a stage's pose on
        // top of the clip, both leave that timeline
        for (long now = STARTED_AT - MARGIN_TICKS; now <= STARTED_AT + WIND_LENGTH + MARGIN_TICKS; now++) {
            if (now == STARTED_AT) {
                state = state.startWindingAt(now);
            }
            state = state.settledAt(now);
            animator.updateAnimationFromState(state);

            for (float partialTick : PARTIAL_TICKS) {
                float clipTick = Math.clamp((now - STARTED_AT) + partialTick, 0.0F, WIND_LENGTH);
                animator.sampleInto(actual, state, now, partialTick);
                assertSamePose(clipPose(WIND, clipTick), actual, "tick " + now + " + " + partialTick);
            }
        }
    }

    @Test
    void sampleInto_holdsTheCockedPose_forAWindWhoseClipRanOutBeforeTheStateWasSettled() {
        // Arrange: a state handed over unsettled after its clip has already run out
        BallistaAnimator animator = new BallistaAnimator();
        BallistaStateMachine unsettled = BallistaStateMachine.unwound().startWindingAt(STARTED_AT);
        animator.updateAnimationFromState(unsettled);
        AnimationPose actual = new AnimationPose(RIG);

        // Act
        animator.sampleInto(actual, unsettled, STARTED_AT + WIND_LENGTH + MARGIN_TICKS, 0.0F);

        // Assert: taking the stage as given would draw a winding machine with no clip left to play, which is the rest
        // pose
        assertSamePose(clipPose(WIND, WIND_LENGTH), actual, "after the clip ran out");
    }

    @Test
    void sampleInto_followsTheFireClip_fromTheCockedPoseToTheRestPose_forAShotWatchedFromItsCommit() {
        // Arrange
        BallistaAnimator animator = new BallistaAnimator();
        BallistaStateMachine state = BallistaStateMachine.cocked();
        AnimationPose actual = new AnimationPose(RIG);

        // Act, Assert: before the commit the cocked pose is held, which is the fire clip's first frame, and after the
        // clip the rest pose is held, which is its last. Holding the cocked pose under the clip or after it would draw
        // the recoil on top of a drawn machine, or snap it back to cocked once the shot has gone
        for (long now = STARTED_AT - MARGIN_TICKS; now <= STARTED_AT + FIRE_LENGTH + MARGIN_TICKS; now++) {
            if (now == STARTED_AT) {
                state = state.fireAt(now);
            }
            state = state.settledAt(now);
            animator.updateAnimationFromState(state);

            for (float partialTick : PARTIAL_TICKS) {
                float clipTick = Math.clamp((now - STARTED_AT) + partialTick, 0.0F, FIRE_LENGTH);
                animator.sampleInto(actual, state, now, partialTick);
                assertSamePose(clipPose(FIRE, clipTick), actual, "tick " + now + " + " + partialTick);
            }
        }
    }

    @Test
    void sampleInto_holdsTheRestPose_forAFiringWhoseClipRanOutBeforeTheStateWasSettled() {
        // Arrange
        BallistaAnimator animator = new BallistaAnimator();
        BallistaStateMachine unsettled = BallistaStateMachine.cocked().fireAt(STARTED_AT);
        animator.updateAnimationFromState(unsettled);
        AnimationPose actual = new AnimationPose(RIG);

        // Act
        animator.sampleInto(actual, unsettled, STARTED_AT + FIRE_LENGTH + MARGIN_TICKS, 0.0F);

        // Assert: taking the stage as given could hold a firing machine anywhere but at rest once its clip is gone
        assertSamePose(new AnimationPose(RIG), actual, "after the clip ran out");
    }

    @Test
    void continuesWinding_whenCockedStateArrivesBeforeLocalClipFinishes() {
        // Arrange
        BallistaAnimator animator = new BallistaAnimator();
        animator.updateAnimationFromState(BallistaStateMachine.unwound().startWindingAt(STARTED_AT));
        BallistaStateMachine receivedState = BallistaStateMachine.cocked();
        long now = STARTED_AT + WIND_LENGTH / 2;
        float partialTick = 0.5F;
        AnimationPose actual = new AnimationPose(RIG);

        // Act
        animator.updateAnimationFromState(receivedState);
        animator.sampleInto(actual, receivedState, now, partialTick);

        // Assert
        assertSamePose(clipPose(WIND, (now - STARTED_AT) + partialTick), actual,
                "an early cocked state must not skip the remaining winding motion");
    }

    @Test
    void continuesFiring_whenUnwoundStateArrivesBeforeLocalClipFinishes() {
        // Arrange
        BallistaAnimator animator = new BallistaAnimator();
        animator.updateAnimationFromState(BallistaStateMachine.cocked().fireAt(STARTED_AT));
        BallistaStateMachine receivedState = BallistaStateMachine.unwound();
        long now = STARTED_AT + FIRE_LENGTH / 2;
        float partialTick = 0.5F;
        AnimationPose actual = new AnimationPose(RIG);

        // Act
        animator.updateAnimationFromState(receivedState);
        animator.sampleInto(actual, receivedState, now, partialTick);

        // Assert
        assertSamePose(clipPose(FIRE, (now - STARTED_AT) + partialTick), actual,
                "an early unwound state must not skip the remaining recoil");
    }

    @Test
    void updateAnimationFromState_playsTheFireClip_inPlaceOfAWindClipStillPlaying() {
        // Arrange: a client whose wind clip still has ticks left when the server's commit reaches it
        BallistaAnimator animator = new BallistaAnimator();
        long windStartedAt = STARTED_AT - WIND_LENGTH + WIND_TICKS_LEFT_WHEN_FIRED;
        animator.updateAnimationFromState(BallistaStateMachine.unwound().startWindingAt(windStartedAt));
        BallistaStateMachine firing = BallistaStateMachine.cocked().fireAt(STARTED_AT);
        AnimationPose actual = new AnimationPose(RIG);

        // Act
        animator.updateAnimationFromState(firing);

        // Assert: an animator that ignored a new clip while one is playing would finish the wind instead, and then
        // hold the rest pose with the recoil never drawn
        for (long now = STARTED_AT; now <= STARTED_AT + FIRE_LENGTH; now++) {
            animator.sampleInto(actual, firing, now, 0.0F);
            assertSamePose(clipPose(FIRE, now - STARTED_AT), actual, "tick " + (now - STARTED_AT) + " of the firing");
        }
    }

    @Test
    void sampleInto_holdsThePlayingClipsLastFrame_atAPartialTickThatIsNotANumber() {
        // Arrange: midway through a wind, whose stage alone would draw the rest pose
        BallistaAnimator animator = new BallistaAnimator();
        BallistaStateMachine winding = BallistaStateMachine.unwound().startWindingAt(STARTED_AT);
        animator.updateAnimationFromState(winding);
        AnimationPose actual = new AnimationPose(RIG);

        // Act
        animator.sampleInto(actual, winding, STARTED_AT + WIND_LENGTH / 2, Float.NaN);

        // Assert: a comparison that let NaN fall through to the stage would draw the rest pose
        assertSamePose(clipPose(WIND, WIND_LENGTH), actual, "a partial tick that is not a number");
    }

    @Test
    void sampleInto_replacesEveryChannel_whateverThePoseHeld() {
        // Arrange: a pose left mid-wind, then sampled for a machine at rest
        BallistaAnimator animator = new BallistaAnimator();
        AnimationPose actual = clipPose(WIND, WIND_LENGTH / 2.0F);

        // Act
        animator.sampleInto(actual, BallistaStateMachine.unwound(), STARTED_AT, 0.0F);

        // Assert: a sample that wrote only the channels it keys would leave the earlier wind on the rig
        assertSamePose(new AnimationPose(RIG), actual, "a machine at rest");
    }

    @Test
    void sampleInto_rejectsAPose_onAnotherRig() {
        // Arrange
        BallistaAnimator animator = new BallistaAnimator();
        AnimationPose otherRig = new AnimationPose(AnimationRig.of("other", List.of(
                BallistaAnimationTargets.PUSHER_TRANSLATION)));

        // Act, Assert
        assertThrows(IllegalArgumentException.class,
                () -> animator.sampleInto(otherRig, BallistaStateMachine.unwound(), STARTED_AT, 0.0F));
    }

    private static AnimationPose clipPose(BakedAnimation clip, float clipTick) {
        AnimationPose pose = new AnimationPose(RIG);
        clip.sampleInto(pose, clipTick);
        return pose;
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
