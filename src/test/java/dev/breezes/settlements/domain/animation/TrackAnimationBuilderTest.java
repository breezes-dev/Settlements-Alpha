package dev.breezes.settlements.domain.animation;

import dev.breezes.settlements.domain.presentation.ArmConfiguration;
import dev.breezes.settlements.domain.presentation.ArmPose;
import dev.breezes.settlements.shared.util.ResourceLocationUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackAnimationBuilderTest {

    private static final int DURATION_TICKS = 30;

    @Test
    void leftArmAndRightArm_combineIntoOneClaimOnEachArm_atOneTick() {
        // Arrange
        KeyframeAnimation clip = clip()
                .leftArm(ArmPose.STRAIGHT)
                .rightArm(ArmPose.STRAIGHT)
                .build();

        // Act
        BakedAnimation baked = BakedAnimation.bake(clip, VillagerAnimationRig.RIG);

        // Assert: both arms are straight so losing either claim changes the result
        assertEquals(ArmPose.STRAIGHT, baked.leftArmClaimAt(0.0F));
        assertEquals(ArmPose.STRAIGHT, baked.rightArmClaimAt(0.0F));
    }

    @Test
    void armConfigurationAt_rejectsASecondClaimOnBothArms_atATickAlreadyClaimed() {
        // Arrange
        TrackAnimationBuilder builder = clip().arms(ArmConfiguration.BOTH_STRAIGHT);

        // Act, Assert
        assertThrows(IllegalArgumentException.class,
                () -> builder.armConfigurationAt(0, ArmConfiguration.BOTH_CROSSED));
    }

    @Test
    void leftArm_rejectsASecondClaimOnTheLeftArm_atATickAlreadyClaimed() {
        // Arrange
        TrackAnimationBuilder builder = clip().arms(ArmConfiguration.BOTH_STRAIGHT);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> builder.leftArm(ArmPose.CROSSED));
    }

    @Test
    void rightArm_rejectsASecondClaimOnTheRightArm_atATickAlreadyClaimed() {
        // Arrange
        TrackAnimationBuilder builder = clip().arms(ArmConfiguration.BOTH_STRAIGHT);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> builder.rightArm(ArmPose.CROSSED));
    }

    private static TrackAnimationBuilder clip() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/test/arms"))
                .durationTicks(DURATION_TICKS);
    }

}
