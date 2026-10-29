package dev.breezes.settlements.application.ai.override;

import dev.breezes.settlements.application.ai.behavior.usecases.villager.support.RepairIronGolemBehavior;
import dev.breezes.settlements.application.ai.behavior.usecases.villager.support.RepairIronGolemConfig;
import dev.breezes.settlements.application.ai.brain.ActivityArbiter;
import dev.breezes.settlements.di.ServerScope;
import dev.breezes.settlements.domain.ai.catalog.BehaviorKey;
import dev.breezes.settlements.domain.ai.memory.MemoryTypeRegistry;
import dev.breezes.settlements.domain.ai.override.OverrideTier;
import dev.breezes.settlements.domain.ai.perception.PerceivedEntities;
import dev.breezes.settlements.domain.entities.VillagerProfessionKey;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * Override policy that sends a smith waiting out a raid's pre-wave window to repair a damaged iron golem it senses.
 */
@ServerScope
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class RepairIronGolemOverridePolicy implements OverridePolicy {

    private static final Set<VillagerProfessionKey> SMITHS = Set.of(
            VillagerProfessionKey.ARMORER,
            VillagerProfessionKey.TOOLSMITH,
            VillagerProfessionKey.WEAPONSMITH);

    private final RepairIronGolemConfig config;

    @Override
    public OverrideTier tier() {
        return OverrideTier.DUTY;
    }

    @Override
    public boolean isAdmissibleDuring(@Nullable Activity activity) {
        // Repair serves only the pre-wave windows
        return activity == Activity.PRE_RAID;
    }

    @Override
    public Optional<OverrideRequest> evaluate(@Nonnull ServerLevel level, @Nonnull BaseVillager villager) {
        // Policies apply to every villager, so a smith's own is scoped here
        if (!SMITHS.contains(villager.getProfession())) {
            return Optional.empty();
        }

        if (villager.getSettlementsBrain().getMemory(MemoryTypeRegistry.REPAIR_IRON_GOLEM_RETRY_COOLDOWN).isPresent()) {
            return Optional.empty();
        }

        if (!ActivityArbiter.isRaidAwaitingWave(level.getRaidAt(villager.blockPosition()))) {
            return Optional.empty();
        }

        // Rejected here so the requested behavior never posts an iron demand, which a pre-wave window cannot fill
        if (!villager.getSettlementsInventory().containsOrBypassed(Items.IRON_INGOT)) {
            return Optional.empty();
        }

        boolean anyGolemNeedsRepair = villager.getSettlementsBrain()
                .getMemory(MemoryTypeRegistry.NEARBY_SENSED_ENTITIES)
                .orElse(PerceivedEntities.empty())
                .ofType(IronGolem.class, golem -> RepairIronGolemBehavior.needsRepair(golem, this.config))
                .findAny()
                .isPresent();
        if (!anyGolemNeedsRepair) {
            return Optional.empty();
        }

        return Optional.of(new OverrideRequest.CatalogBehavior(BehaviorKey.REPAIR_IRON_GOLEM));
    }

}
