package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LanternCarryAnimationsTest {

    private static final float DELTA = 0.0001F;
    private static final int ARMS_CROSSED = VillagerAnimationRig.RIG.indexOf(AnimationTargets.ARMS_CROSSED_ROTATION);

    @Test
    void gesturesMeetTheHoldPose_withoutAnEndpointJump() {
        // Arrange
        BakedAnimation show = bake(LanternCarryAnimations.show());
        BakedAnimation hold = bake(LanternCarryAnimations.hold());
        BakedAnimation stow = bake(LanternCarryAnimations.stow());

        // Act
        float heldPitch = armPitch(hold, 0.0F);
        float showEndPitch = armPitch(show, InteractAnimations.INTERACT_DURATION_TICKS);
        float stowStartPitch = armPitch(stow, 0.0F);
        float showStartPitch = armPitch(show, 0.0F);
        float stowEndPitch = armPitch(stow, InteractAnimations.INTERACT_DURATION_TICKS);

        // Assert
        assertEquals(heldPitch, showEndPitch, DELTA);
        assertEquals(heldPitch, stowStartPitch, DELTA);
        assertEquals(heldPitch, armPitch(hold, InteractAnimations.INTERACT_DURATION_TICKS), DELTA);
        assertEquals(0.0F, showStartPitch, DELTA);
        assertEquals(0.0F, stowEndPitch, DELTA);
    }

    @Test
    void gesturesReachTheInteractPeak_onTheInteractPeakTick() {
        // Arrange
        BakedAnimation interact = bake(InteractAnimations.interact());
        BakedAnimation show = bake(LanternCarryAnimations.show());
        BakedAnimation stow = bake(LanternCarryAnimations.stow());
        int peakTick = InteractAnimations.INTERACT_PEAK_TICK;

        // Act
        float interactPeakPitch = armPitch(interact, peakTick);

        // Assert
        assertEquals(interactPeakPitch, armPitch(show, peakTick), DELTA);
        assertEquals(interactPeakPitch, armPitch(stow, peakTick), DELTA);
    }

    @Test
    void carryClipsAddOnlyCrossedArmRotation_withoutOwnershipOrArmClaims() {
        // Arrange
        AnimationContribution contribution = new AnimationContribution(VillagerAnimationRig.RIG);
        List<KeyframeAnimation> clips = List.of(
                LanternCarryAnimations.hold(), LanternCarryAnimations.show(), LanternCarryAnimations.stow());

        for (KeyframeAnimation clip : clips) {
            // Act
            bake(clip).contribute(contribution, InteractAnimations.INTERACT_PEAK_TICK);

            // Assert
            assertNull(contribution.getLeftArmClaim());
            assertNull(contribution.getRightArmClaim());
            for (int channel = 0; channel < VillagerAnimationRig.RIG.getChannelCount(); channel++) {
                assertEquals(channel == ARMS_CROSSED, contribution.isKeyed(channel));
                assertEquals(0.0F, contribution.coverage(channel), DELTA);
            }
        }
    }

    private static BakedAnimation bake(KeyframeAnimation clip) {
        return BakedAnimation.bake(clip, VillagerAnimationRig.RIG);
    }

    private static float armPitch(BakedAnimation clip, float elapsedTicks) {
        AnimationContribution contribution = new AnimationContribution(VillagerAnimationRig.RIG);
        clip.contribute(contribution, elapsedTicks);
        return contribution.value(ARMS_CROSSED, 0);
    }

}
