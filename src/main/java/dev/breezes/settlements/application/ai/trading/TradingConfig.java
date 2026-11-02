package dev.breezes.settlements.application.ai.trading;

import dev.breezes.settlements.application.ai.behavior.runtime.timing.BehaviorTimingConfig;
import dev.breezes.settlements.domain.time.ClockTicks;
import dev.breezes.settlements.infrastructure.config.annotations.BehaviorConfig;
import dev.breezes.settlements.infrastructure.config.annotations.ConfigurationType;
import dev.breezes.settlements.infrastructure.config.annotations.integers.IntegerConfig;

/**
 * Timing and negotiation settings for villager trades.
 * The BehaviorTimingConfig methods expose the initiator's cooldowns.
 */
@BehaviorConfig(name = "trading", type = ConfigurationType.BEHAVIOR)
public record TradingConfig(

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "initiate_precondition_cooldown_seconds",
                description = "Seconds between each villager's scans for a nearby villager selling an item it wants.",
                defaultValue = 30,
                min = 10)
        int partnerScanIntervalSeconds,

        // Short retry cooldowns are intentional: an attempt still requires a nearby willing seller.
        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "initiate_behavior_cooldown_seconds_min",
                description = "Minimum seconds a villager waits after a trade it initiated before initiating another.",
                defaultValue = 5,
                min = 1)
        int initiateBehaviorCooldownSecondsMin,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "initiate_behavior_cooldown_seconds_max",
                description = "Maximum seconds a villager waits after a trade it initiated before initiating another.",
                defaultValue = 15,
                min = 1)
        int initiateBehaviorCooldownSecondsMax,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "accept_precondition_cooldown_seconds",
                description = "Seconds between responder invite checks.",
                defaultValue = 2,
                min = 1)
        int acceptPreconditionCooldownSeconds,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "accept_behavior_cooldown_seconds_min",
                description = "Minimum responder behavior cooldown in seconds.",
                defaultValue = 5,
                min = 1)
        int acceptBehaviorCooldownSecondsMin,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "accept_behavior_cooldown_seconds_max",
                description = "Maximum responder behavior cooldown in seconds.",
                defaultValue = 15,
                min = 1)
        int acceptBehaviorCooldownSecondsMax,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "max_negotiation_rounds",
                description = "Maximum number of negotiation rounds before villagers walk away.",
                defaultValue = 10,
                min = 1)
        int maxNegotiationRounds,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "invite_timeout_seconds",
                description = "Seconds before an unanswered trade invite expires.",
                defaultValue = 10,
                min = 1)
        int inviteTimeoutSeconds,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "negotiation_round_duration_seconds",
                description = "Seconds spent on each negotiation round.",
                defaultValue = 5,
                min = 1)
        int negotiationRoundDurationSeconds,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "opening_offer_duration_seconds",
                description = "Seconds spent presenting the opening offer.",
                defaultValue = 2,
                min = 1)
        int openingOfferDurationSeconds,

        @IntegerConfig(type = ConfigurationType.BEHAVIOR,
                identifier = "walkaway_duration_seconds",
                description = "Seconds spent on the walk-away phase before the session closes.",
                defaultValue = 1,
                min = 1)
        int walkawayDurationSeconds

) implements BehaviorTimingConfig {

    public TradingConfig {
        if (initiateBehaviorCooldownSecondsMin > initiateBehaviorCooldownSecondsMax) {
            throw new IllegalArgumentException("Initiate behavior cooldown min must be <= max");
        }
        if (acceptBehaviorCooldownSecondsMin > acceptBehaviorCooldownSecondsMax) {
            throw new IllegalArgumentException("Accept behavior cooldown min must be <= max");
        }
    }

    @Override
    public int preconditionCheckCooldownMin() {
        return this.partnerScanIntervalSeconds;
    }

    @Override
    public int preconditionCheckCooldownMax() {
        return this.partnerScanIntervalSeconds;
    }

    @Override
    public int behaviorCooldownMin() {
        return this.initiateBehaviorCooldownSecondsMin;
    }

    @Override
    public int behaviorCooldownMax() {
        return this.initiateBehaviorCooldownSecondsMax;
    }

    public ClockTicks negotiationRoundDuration() {
        return ClockTicks.seconds(this.negotiationRoundDurationSeconds);
    }

    public ClockTicks openingOfferDuration() {
        return ClockTicks.seconds(this.openingOfferDurationSeconds);
    }

    public ClockTicks walkawayDuration() {
        return ClockTicks.seconds(this.walkawayDurationSeconds);
    }

}
