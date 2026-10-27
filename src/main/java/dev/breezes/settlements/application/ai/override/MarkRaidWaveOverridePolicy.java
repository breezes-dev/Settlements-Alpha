package dev.breezes.settlements.application.ai.override;

import dev.breezes.settlements.application.ai.brain.ActivityArbiter;
import dev.breezes.settlements.application.ai.raid.RaidWaveMarkRegistry;
import dev.breezes.settlements.di.ServerScope;
import dev.breezes.settlements.domain.ai.catalog.BehaviorKey;
import dev.breezes.settlements.domain.ai.override.OverrideTier;
import dev.breezes.settlements.domain.entities.Expertise;
import dev.breezes.settlements.domain.entities.VillagerProfessionKey;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import dev.breezes.settlements.infrastructure.minecraft.mixins.RaidMixin;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Requests a qualified cartographer's sighting of an unmarked pre-wave window.
 */
@ServerScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class MarkRaidWaveOverridePolicy implements OverridePolicy {

    private final RaidWaveMarkRegistry marks;

    @Override
    public OverrideTier tier() {
        return OverrideTier.DUTY;
    }

    @Override
    public boolean isAdmissibleDuring(@Nullable Activity activity) {
        return activity == Activity.PRE_RAID;
    }

    @Override
    public Optional<OverrideRequest> evaluate(@Nonnull ServerLevel level, @Nonnull BaseVillager villager) {
        if (!VillagerProfessionKey.CARTOGRAPHER.equals(villager.getProfession())
                || villager.getExpertise().getLevel() < Expertise.JOURNEYMAN.getLevel()) {
            return Optional.empty();
        }

        Raid raid = level.getRaidAt(villager.blockPosition());
        if (!ActivityArbiter.isRaidAwaitingWave(raid)
                || ((RaidMixin) raid).getRaidWaveSpawnPos().isEmpty()
                || !this.marks.isAvailable(raid, villager.getUUID())) {
            return Optional.empty();
        }

        // Evaluation can lose arbitration, so only the behavior's start may claim this window.
        return Optional.of(new OverrideRequest.CatalogBehavior(BehaviorKey.MARK_RAID_WAVE));
    }

}
