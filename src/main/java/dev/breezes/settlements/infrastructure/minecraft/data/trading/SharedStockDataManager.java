package dev.breezes.settlements.infrastructure.minecraft.data.trading;

import dev.breezes.settlements.domain.economy.catalog.StockPolicy;
import dev.breezes.settlements.domain.economy.catalog.StockPolicyCodec;
import dev.breezes.settlements.infrastructure.minecraft.data.framework.CodecJsonDataManager;
import jakarta.inject.Inject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the stock policies every profession shares from {@code settlements/shared_stock}, where each file is a plain
 * {@code stock} list with no profession.
 * A later file replaces an earlier file's policy with the same id.
 */
public class SharedStockDataManager extends CodecJsonDataManager<List<StockPolicy>> {

    private static final String DIRECTORY_PATH = "settlements/shared_stock";

    private volatile List<StockPolicy> sharedStockPolicies = List.of();

    @Inject
    public SharedStockDataManager() {
        super(DIRECTORY_PATH, StockPolicyCodec.CODEC.listOf().fieldOf("stock").codec());
    }

    @Override
    protected String label() {
        return "shared stock";
    }

    @Override
    protected void onReloaded(@Nonnull Map<ResourceLocation, List<StockPolicy>> values) {
        Map<String, StockPolicy> mergedById = new LinkedHashMap<>();
        for (List<StockPolicy> file : values.values()) {
            for (StockPolicy policy : file) {
                mergedById.put(policy.id(), policy);
            }
        }

        this.sharedStockPolicies = List.copyOf(mergedById.values());
    }

    /**
     * Returns the merged shared policies as an immutable list. A reload replaces the list rather than changing it, so
     * a list already returned never changes.
     */
    public List<StockPolicy> sharedStockPolicies() {
        return this.sharedStockPolicies;
    }

}
