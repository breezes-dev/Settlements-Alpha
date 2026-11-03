package dev.breezes.settlements.infrastructure.minecraft.persistence;

import dev.breezes.settlements.di.ServerScope;
import dev.breezes.settlements.domain.ai.naming.VillagerNameDirectory;
import dev.breezes.settlements.domain.ai.naming.VillagerNamePoolRegistry;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Resolves persisted villager names with fallbacks from the current datapack pool.
 */
@ServerScope
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
public final class VillagerNameDirectoryImpl implements VillagerNameDirectory {

    private final VillagerNamePoolRegistry namePools;

    @Override
    public String resolve(@Nonnull UUID uuid) {
        return savedData().resolve(uuid, this.namePools.pool());
    }

    @Override
    public void upsert(@Nonnull UUID uuid, @Nonnull String name) {
        savedData().upsert(uuid, name);
    }

    @Override
    public void remove(@Nonnull UUID uuid) {
        savedData().remove(uuid);
    }

    private static VillagerNameSavedData savedData() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("VillagerNameDirectory used while no MinecraftServer is running");
        }

        return VillagerNameSavedData.get(server);
    }

}
