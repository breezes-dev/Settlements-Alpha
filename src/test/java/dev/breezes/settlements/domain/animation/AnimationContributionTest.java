package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimationContributionTest {

    private static final AnimationRig RIG = VillagerAnimationRig.RIG;

    private static final int HEAD_ROTATION = RIG.indexOf(AnimationTargets.HEAD_ROTATION);

    @Test
    void own_clampsCoverageIntoTheUnitRange() {
        // Arrange
        AnimationContribution over = new AnimationContribution(RIG);
        AnimationContribution under = new AnimationContribution(RIG);

        // Act
        over.own(VillagerAnimationRig.HEAD_ROTATION, 1.5F);
        under.own(VillagerAnimationRig.HEAD_ROTATION, -0.5F);

        // Assert
        assertEquals(1.0F, over.coverage(HEAD_ROTATION));
        assertEquals(0.0F, under.coverage(HEAD_ROTATION));
    }

    @Test
    void suppress_clampsStrengthIntoTheUnitRange() {
        // Arrange
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act
        contribution.suppress(SuppressibleComponent.BREATH, 1.5F);
        contribution.suppress(SuppressibleComponent.BLINK, -0.5F);

        // Assert
        assertEquals(1.0F, contribution.suppression(SuppressibleComponent.BREATH));
        assertEquals(0.0F, contribution.suppression(SuppressibleComponent.BLINK));
    }

    @Test
    void own_rejectsAMaskOnAnotherRig_evenWhenThisRigHoldsItsChannels() {
        // Arrange
        AnimationRig otherRig = AnimationRig.of("other", List.of(AnimationTargets.HEAD_ROTATION));
        AnimationContribution contribution = new AnimationContribution(RIG);

        // Act, Assert
        assertThrows(IllegalArgumentException.class, () -> contribution.own(otherRig.getFullBody(), 1.0F));
    }

}
