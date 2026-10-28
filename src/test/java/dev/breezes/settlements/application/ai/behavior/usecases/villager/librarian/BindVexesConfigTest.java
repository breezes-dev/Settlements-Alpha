package dev.breezes.settlements.application.ai.behavior.usecases.villager.librarian;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BindVexesConfigTest {

    private static final int ANY_CADENCE_SECONDS = 30;
    private static final Map<String, Integer> ANY_VEXES_PER_CAST = Map.of("novice", 2);
    private static final int ANY_BIND_DURATION_SECONDS = 60;
    private static final int ANY_EXPERIENCE_REWARD = 1;

    @Test
    void rejects_aLevelThatBindsNoVex() {
        // Arrange
        Map<String, Integer> vexesPerCast = Map.of("novice", 2, "apprentice", 0);

        // Act, Assert: dropping validation would load a level that channels every cast for nothing.
        assertThrows(IllegalArgumentException.class, () -> new BindVexesConfig(
                ANY_CADENCE_SECONDS, vexesPerCast, ANY_BIND_DURATION_SECONDS, ANY_EXPERIENCE_REWARD));
    }

    @Test
    void accepts_aLevelThatBindsOneVex() {
        // Arrange
        Map<String, Integer> vexesPerCast = Map.of("novice", 1);

        // Act, Assert: a bound set one too high would refuse a pack that binds a single vex at a level.
        assertDoesNotThrow(() -> new BindVexesConfig(
                ANY_CADENCE_SECONDS, vexesPerCast, ANY_BIND_DURATION_SECONDS, ANY_EXPERIENCE_REWARD));
    }

    @Test
    void rejects_aNegativeExperienceReward() {
        // Arrange
        int negativeReward = -1;

        // Act, Assert: dropping validation would admit a reward outside the nonnegative configuration contract.
        assertThrows(IllegalArgumentException.class, () -> new BindVexesConfig(
                ANY_CADENCE_SECONDS, ANY_VEXES_PER_CAST, ANY_BIND_DURATION_SECONDS, negativeReward));
    }

    @Test
    void accepts_disablingExperienceRewards() {
        // Arrange
        int disabledReward = 0;

        // Act, Assert: an exclusive lower bound would prevent operators from disabling the reward.
        assertDoesNotThrow(() -> new BindVexesConfig(
                ANY_CADENCE_SECONDS, ANY_VEXES_PER_CAST, ANY_BIND_DURATION_SECONDS, disabledReward));
    }

}
