package dev.breezes.settlements.application.ai.override;

import dev.breezes.settlements.application.ai.behavior.usecases.villager.librarian.VexBinding;
import dev.breezes.settlements.application.ai.brain.ActivityArbiter;
import dev.breezes.settlements.bootstrap.registry.activities.ActivityRegistry;
import dev.breezes.settlements.di.ServerScope;
import dev.breezes.settlements.domain.ai.catalog.BehaviorKey;
import dev.breezes.settlements.domain.ai.memory.MemoryTypeRegistry;
import dev.breezes.settlements.domain.ai.override.OverrideTier;
import dev.breezes.settlements.domain.ai.perception.PerceivedEntities;
import dev.breezes.settlements.domain.entities.VillagerProfessionKey;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.schedule.Activity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Override policy that sends a librarian sheltering from a raid wave to bind the unbound vexes it senses.
 */
@ServerScope
@NoArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class BindVexesOverridePolicy implements OverridePolicy {

    @Override
    public OverrideTier tier() {
        return OverrideTier.DUTY;
    }

    @Override
    public boolean isAdmissibleDuring(@Nullable Activity activity) {
        // Binding serves only a live wave
        return activity == ActivityRegistry.RAID_HIDE;
    }

    @Override
    public Optional<OverrideRequest> evaluate(@Nonnull ServerLevel level, @Nonnull BaseVillager villager) {
        // Policies apply to every villager, so a librarian's own is scoped here
        if (!VillagerProfessionKey.LIBRARIAN.equals(villager.getProfession())) {
            return Optional.empty();
        }

        if (villager.getSettlementsBrain().getMemory(MemoryTypeRegistry.BIND_VEXES_COOLDOWN).isPresent()) {
            return Optional.empty();
        }

        if (!ActivityArbiter.isRaidWaveLive(level.getRaidAt(villager.blockPosition()))) {
            return Optional.empty();
        }

        // A channel with nothing to bind would play for nothing
        boolean anyBindable = villager.getSettlementsBrain().getMemory(MemoryTypeRegistry.NEARBY_HOSTILES)
                .orElse(PerceivedEntities.empty())
                .ofType(LivingEntity.class, hostile -> VexBinding.isBindable(hostile, level))
                .findAny()
                .isPresent();
        if (!anyBindable) {
            return Optional.empty();
        }

        return Optional.of(new OverrideRequest.CatalogBehavior(BehaviorKey.BIND_VEXES));
    }

}
