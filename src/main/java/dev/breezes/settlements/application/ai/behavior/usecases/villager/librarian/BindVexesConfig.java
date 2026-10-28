package dev.breezes.settlements.application.ai.behavior.usecases.villager.librarian;

import dev.breezes.settlements.application.config.constants.BehaviorConfigConstants;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.infrastructure.config.annotations.BehaviorConfig;
import dev.breezes.settlements.infrastructure.config.annotations.ConfigurationType;
import dev.breezes.settlements.infrastructure.config.annotations.integers.IntegerConfig;
import dev.breezes.settlements.infrastructure.config.annotations.maps.MapConfig;
import dev.breezes.settlements.infrastructure.config.annotations.maps.MapEntry;
import org.apache.commons.lang3.Validate;

import java.util.Map;

/**
 * Startup configuration for a librarian binding the vexes near it during a raid wave.
 */
@BehaviorConfig(name = "bind_vexes", type = ConfigurationType.BEHAVIOR)
public record BindVexesConfig(

        @IntegerConfig(
                type = ConfigurationType.BEHAVIOR,
                identifier = "cadence_seconds",
                description = "Seconds a librarian waits after binding vexes before it binds again",
                defaultValue = 30,
                min = 1,
                max = 600)
        int cadenceSeconds,

        @MapConfig(
                type = ConfigurationType.BEHAVIOR,
                identifier = "expertise_vexes_per_cast",
                description = "Most vexes a librarian binds at once, by expertise level. Each must be at least 1",
                deserializer = "StringToInteger",
                defaultValue = {
                        @MapEntry(key = "novice", value = "2"),
                        @MapEntry(key = "apprentice", value = "3"),
                        @MapEntry(key = "journeyman", value = "5"),
                        @MapEntry(key = "expert", value = "7"),
                        @MapEntry(key = "master", value = "10")
                })
        Map<String, Integer> expertiseVexesPerCast,

        @IntegerConfig(
                type = ConfigurationType.BEHAVIOR,
                identifier = "bind_duration_seconds",
                description = "Seconds a bound vex stays held and weakened",
                defaultValue = 60,
                min = 1,
                max = 600)
        int bindDurationSeconds,

        @IntegerConfig(
                type = ConfigurationType.BEHAVIOR,
                identifier = BehaviorConfigConstants.EXPERIENCE_REWARD_IDENTIFIER,
                description = BehaviorConfigConstants.EXPERIENCE_REWARD_DESCRIPTION,
                defaultValue = 1,
                min = 0)
        int experienceReward

) {

    /**
     * Requires every expertise level to bind at least one vex and a nonnegative experience reward; zero disables the
     * reward.
     *
     * @throws IllegalArgumentException if a level binds fewer than one vex or experienceReward is negative
     */
    public BindVexesConfig {
        // A level that binds nobody would still channel on every cast, with nothing to show for it
        Validate.isTrue(expertiseVexesPerCast.values().stream().allMatch(count -> count >= 1),
                "expertise_vexes_per_cast values must each be at least 1: %s", expertiseVexesPerCast);
        Validate.isTrue(experienceReward >= 0, "experienceReward must be at least 0: %s", experienceReward);
    }

    public ClockTicks cadence() {
        return ClockTicks.seconds(this.cadenceSeconds);
    }

    public ClockTicks bindDuration() {
        return ClockTicks.seconds(this.bindDurationSeconds);
    }

}
