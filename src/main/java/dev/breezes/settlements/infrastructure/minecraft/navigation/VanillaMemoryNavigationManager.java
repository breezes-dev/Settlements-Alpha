package dev.breezes.settlements.infrastructure.minecraft.navigation;

import dev.breezes.settlements.domain.ai.navigation.AgilitySpeedResolver;
import dev.breezes.settlements.domain.ai.navigation.INavigationManager;
import dev.breezes.settlements.domain.ai.navigation.NavigationType;
import dev.breezes.settlements.domain.genetics.GeneType;
import dev.breezes.settlements.domain.world.location.Location;
import dev.breezes.settlements.infrastructure.minecraft.entities.villager.BaseVillager;
import lombok.AllArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nonnull;
import java.util.Optional;

@AllArgsConstructor
public class VanillaMemoryNavigationManager<T extends BaseVillager> implements INavigationManager<T> {

    private final T villager;

    @Override
    public Optional<Location> getNavigationTarget() {
        return villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .map(PositionTracker::currentBlockPosition)
                .map(blockPos -> Location.of(blockPos, villager.level()));
    }

    @Override
    public boolean isNavigating() {
        return this.villager.getNavigation().isInProgress();
    }

    @Override
    public void stop() {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    @Override
    public void navigateTo(@Nonnull Location target, @Nonnull NavigationType type, int completionRange) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new NavigationIntentWalkTarget(target.toBlockPos(), this.speedFor(type), completionRange, type));
    }

    @Override
    public void walkTo(@Nonnull Location target, int completionRange) {
        this.navigateTo(target, NavigationType.WALK, completionRange);
    }

    @Override
    public float speedFor(@Nonnull NavigationType type) {
        return AgilitySpeedResolver.resolve(type, this.agility());
    }

    /**
     * Returns the intent the villager moves under.
     */
    public NavigationType resolveActiveNavigationIntent() {
        WalkTarget walkTarget = this.villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (walkTarget == null) {
            return NavigationType.WALK;
        }

        // If an intent is explicitly declared, use it
        if (walkTarget instanceof NavigationIntentWalkTarget intentWalkTarget) {
            return intentWalkTarget.navigationIntent;
        }

        // Return closest navigation type based on speed
        return AgilitySpeedResolver.resolveNearestNavigationType(walkTarget.getSpeedModifier(), this.agility());
    }

    @Override
    public boolean canReach(@Nonnull Location target, double distance) {
        // Use vanilla's own pathfinder to produce a trial path
        Path path = this.villager.getNavigation().createPath(target.toBlockPos(), Mth.floor(distance));
        return path != null && path.canReach();
    }

    private double agility() {
        return this.villager.getGenetics().getGeneValue(GeneType.AGILITY);
    }

    /**
     * A walk target that records the navigation intent it was set with.
     */
    private static final class NavigationIntentWalkTarget extends WalkTarget {

        private final NavigationType navigationIntent;

        private NavigationIntentWalkTarget(@Nonnull BlockPos pos,
                                           float speedModifier,
                                           int closeEnoughDist,
                                           @Nonnull NavigationType navigationIntent) {
            super(pos, speedModifier, closeEnoughDist);
            this.navigationIntent = navigationIntent;
        }

    }

}
