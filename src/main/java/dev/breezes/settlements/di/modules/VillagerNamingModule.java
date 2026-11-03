package dev.breezes.settlements.di.modules;

import dagger.Binds;
import dagger.Module;
import dev.breezes.settlements.domain.ai.naming.VillagerNamePoolRegistry;
import dev.breezes.settlements.infrastructure.minecraft.data.naming.VillagerNamePoolDataManager;

/**
 * Binds {@link VillagerNamePoolRegistry} to its datapack-backed implementation.
 */
@Module
public interface VillagerNamingModule {

    @Binds
    VillagerNamePoolRegistry villagerNamePoolRegistry(VillagerNamePoolDataManager dataManager);

}
