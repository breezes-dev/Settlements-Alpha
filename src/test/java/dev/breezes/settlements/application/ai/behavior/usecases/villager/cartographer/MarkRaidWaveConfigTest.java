package dev.breezes.settlements.application.ai.behavior.usecases.villager.cartographer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarkRaidWaveConfigTest {

    @Test
    void rejects_aNegativeExperienceReward() {
        // Arrange
        int negativeReward = -1;

        // Act, Assert: dropping validation would admit a reward outside the nonnegative configuration contract.
        assertThrows(IllegalArgumentException.class, () -> new MarkRaidWaveConfig(negativeReward));
    }

    @Test
    void accepts_disablingExperienceRewards() {
        // Arrange
        int disabledReward = 0;

        // Act, Assert: an exclusive lower bound would prevent operators from disabling the reward.
        assertDoesNotThrow(() -> new MarkRaidWaveConfig(disabledReward));
    }

}
