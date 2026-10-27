package dev.breezes.settlements.application.ai.behavior.usecases.villager.cartographer;

import dev.breezes.settlements.application.config.constants.BehaviorConfigConstants;
import dev.breezes.settlements.infrastructure.config.annotations.BehaviorConfig;
import dev.breezes.settlements.infrastructure.config.annotations.ConfigurationType;
import dev.breezes.settlements.infrastructure.config.annotations.integers.IntegerConfig;
import org.apache.commons.lang3.Validate;

/**
 * Startup configuration for marking a raid's next wave.
 */
@BehaviorConfig(name = "mark_raid_wave", type = ConfigurationType.BEHAVIOR)
public record MarkRaidWaveConfig(

        @IntegerConfig(
                type = ConfigurationType.BEHAVIOR,
                identifier = BehaviorConfigConstants.EXPERIENCE_REWARD_IDENTIFIER,
                description = BehaviorConfigConstants.EXPERIENCE_REWARD_DESCRIPTION,
                defaultValue = 1,
                min = 0)
        int experienceReward

) {

    /**
     * Requires a nonnegative experience reward; zero disables the reward.
     *
     * @throws IllegalArgumentException if experienceReward is negative
     */
    public MarkRaidWaveConfig {
        Validate.isTrue(experienceReward >= 0, "experienceReward must be at least 0: %s", experienceReward);
    }

}
