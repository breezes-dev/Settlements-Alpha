package dev.breezes.settlements.infrastructure.minecraft.data.naming;

import dev.breezes.settlements.domain.ai.naming.VillagerNamePool;
import dev.breezes.settlements.domain.ai.naming.VillagerNamePoolCodec;
import dev.breezes.settlements.domain.ai.naming.VillagerNamePoolRegistry;
import dev.breezes.settlements.infrastructure.minecraft.data.framework.CodecJsonDataManager;
import jakarta.inject.Inject;
import lombok.CustomLog;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads villager name files from every namespace into a pool replaced on each reload.
 * <p>
 * New file ids contribute names, and a pack replaces a file by overriding its id. Names retain file id order, then
 * each file's own order, with the first spelling kept when names repeat ignoring case.
 */
@CustomLog(topic = "resource_loading")
public final class VillagerNamePoolDataManager extends CodecJsonDataManager<VillagerNamePool>
        implements VillagerNamePoolRegistry {

    private static final String DIRECTORY_PATH = "settlements/names/villagers";

    private VillagerNamePool pool = VillagerNamePool.EMPTY;

    @Inject
    public VillagerNamePoolDataManager() {
        super(DIRECTORY_PATH, VillagerNamePoolCodec.CODEC);
    }

    @Override
    protected String label() {
        return "villager name pool";
    }

    @Override
    protected void onReloaded(@Nonnull Map<ResourceLocation, VillagerNamePool> values) {
        List<String> names = new ArrayList<>();
        for (VillagerNamePool file : values.values()) {
            names.addAll(file.names());
        }
        this.pool = new VillagerNamePool(names);

        if (this.pool.names().isEmpty()) {
            log.error("No villager names are loaded from {}; generated villager names are unavailable", DIRECTORY_PATH);
        }
    }

    @Override
    public VillagerNamePool pool() {
        return this.pool;
    }

}
