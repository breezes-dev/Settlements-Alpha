package dev.breezes.settlements.infrastructure.minecraft.behavior.ambient;

import dev.breezes.settlements.infrastructure.config.annotations.BehaviorConfig;
import dev.breezes.settlements.infrastructure.config.annotations.ConfigurationType;
import dev.breezes.settlements.infrastructure.config.annotations.integers.IntegerConfig;

@BehaviorConfig(name = "village_poi_bound", type = ConfigurationType.FEATURE)
public record VillagePoiBoundConfig(

        @IntegerConfig(
                type = ConfigurationType.FEATURE,
                identifier = "max_poi_distance",
                description = "Radius in blocks around a villager's remembered point of interest for random strolling. " +
                        "Outside this area, villagers will try to return toward the nearest remembered location.",
                defaultValue = 48,
                min = 16,
                max = 512)
        int maxPoiDistanceBlocks

) {
}
