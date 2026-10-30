package dev.breezes.settlements.infrastructure.minecraft.data.trading;

import dev.breezes.settlements.domain.economy.catalog.DemandEntry;
import dev.breezes.settlements.domain.economy.catalog.ItemMatch;
import dev.breezes.settlements.domain.economy.catalog.OfferEntry;
import dev.breezes.settlements.domain.economy.catalog.StockPolicy;
import dev.breezes.settlements.domain.economy.catalog.StockPolicyCodec;
import dev.breezes.settlements.domain.economy.catalog.SupplyEntry;
import dev.breezes.settlements.domain.economy.catalog.TradeCatalogRegistry;
import dev.breezes.settlements.domain.entities.VillagerProfessionKey;
import dev.breezes.settlements.infrastructure.minecraft.data.framework.ProfessionCatalogDataManager;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TradeCatalogDataManager extends ProfessionCatalogDataManager<StockPolicy> implements TradeCatalogRegistry {

    private static final String DIRECTORY_PATH = "settlements/trade_catalog";

    private final SharedStockDataManager sharedStockDataManager;

    @Nullable
    private volatile MergedView mergedView;

    @Inject
    public TradeCatalogDataManager(@Nonnull SharedStockDataManager sharedStockDataManager) {
        super(DIRECTORY_PATH, StockPolicyCodec.CODEC, "stock");
        this.sharedStockDataManager = sharedStockDataManager;
    }

    @Override
    protected String label() {
        return "trade catalog";
    }

    @Override
    protected String idOf(StockPolicy value) {
        return value.id();
    }

    @Override
    public List<StockPolicy> stockPoliciesFor(@Nonnull VillagerProfessionKey profession) {
        return this.projectionFor(profession).policies();
    }

    @Override
    public List<OfferEntry> findOffers(@Nonnull VillagerProfessionKey sellerProfession, @Nonnull ItemMatch want) {
        return this.offersFor(sellerProfession).stream()
                .filter(entry -> matchPriority(entry.match(), want) >= 0)
                .sorted((left, right) -> Integer.compare(matchPriority(right.match(), want), matchPriority(left.match(), want)))
                .toList();
    }

    @Override
    public List<DemandEntry> findDemands(@Nonnull VillagerProfessionKey buyerProfession, @Nonnull ItemMatch want) {
        return this.demandsFor(buyerProfession).stream()
                .filter(entry -> matchPriority(entry.match(), want) >= 0)
                .sorted((left, right) -> Integer.compare(matchPriority(right.match(), want), matchPriority(left.match(), want)))
                .toList();
    }

    @Override
    public List<OfferEntry> offersFor(@Nonnull VillagerProfessionKey profession) {
        return this.projectionFor(profession).offers();
    }

    @Override
    public List<DemandEntry> demandsFor(@Nonnull VillagerProfessionKey profession) {
        return this.projectionFor(profession).demands();
    }

    @Override
    public List<SupplyEntry> supplyFor(@Nonnull VillagerProfessionKey profession) {
        return this.projectionFor(profession).supply();
    }

    /**
     * Returns the projections for a profession, which are the shared stock alone for a profession with no catalog file.
     * The merged view is rebuilt on first use after either source reloads, so it never depends on reload order.
     */
    private Projection projectionFor(@Nonnull VillagerProfessionKey profession) {
        List<StockPolicy> sharedPolicies = this.sharedStockDataManager.sharedStockPolicies();
        Map<VillagerProfessionKey, List<StockPolicy>> professionPolicies = this.byProfession();

        MergedView view = this.mergedView;
        if (view == null || view.sharedSource() != sharedPolicies || view.professionSource() != professionPolicies) {
            view = MergedView.build(sharedPolicies, professionPolicies);
            this.mergedView = view;
        }

        return view.byProfession().getOrDefault(profession, view.sharedOnly());
    }

    private static int matchPriority(@Nonnull ItemMatch catalogMatch, @Nonnull ItemMatch want) {
        return switch (catalogMatch) {
            case ItemMatch.ItemRef itemRef -> switch (want) {
                case ItemMatch.ItemRef wantRef -> itemRef.id().equals(wantRef.id()) ? 3 : -1;
                case ItemMatch.TagRef ignored -> 0; // cross-kind: include as candidate, scanner resolves
            };
            case ItemMatch.TagRef tagRef -> switch (want) {
                case ItemMatch.ItemRef ignored -> 0; // cross-kind: include as candidate, scanner resolves
                case ItemMatch.TagRef wantTagRef -> tagRef.tag().equals(wantTagRef.tag()) ? 3 : -1;
            };
        };
    }

    private record Projection(List<StockPolicy> policies,
                              List<OfferEntry> offers,
                              List<DemandEntry> demands,
                              List<SupplyEntry> supply) {

        /**
         * Shared policies come first in their own order; a profession policy with a shared policy's id takes that
         * policy's position, and the remaining profession policies follow in their own order.
         */
        static Projection merge(List<StockPolicy> sharedPolicies, List<StockPolicy> professionPolicies) {
            Map<String, StockPolicy> mergedById = new LinkedHashMap<>();
            for (StockPolicy policy : sharedPolicies) {
                mergedById.put(policy.id(), policy);
            }
            for (StockPolicy policy : professionPolicies) {
                mergedById.put(policy.id(), policy);
            }

            List<StockPolicy> policies = List.copyOf(mergedById.values());
            List<OfferEntry> offers = new ArrayList<>();
            List<DemandEntry> demands = new ArrayList<>();
            List<SupplyEntry> supply = new ArrayList<>();
            for (StockPolicy policy : policies) {
                if (policy.offer() != null) {
                    offers.add(policy.toOfferEntry());
                }
                if (policy.restock() != null) {
                    demands.add(policy.toDemandEntry());
                }
                if (policy.dump() != null) {
                    supply.add(policy.toSupplyEntry());
                }
            }

            return new Projection(policies, List.copyOf(offers), List.copyOf(demands), List.copyOf(supply));
        }

    }

    /**
     * The sources a view was built from are kept so a reload of either is detected by reference identity.
     */
    private record MergedView(List<StockPolicy> sharedSource,
                              Map<VillagerProfessionKey, List<StockPolicy>> professionSource,
                              Map<VillagerProfessionKey, Projection> byProfession,
                              Projection sharedOnly) {

        static MergedView build(List<StockPolicy> sharedPolicies,
                                Map<VillagerProfessionKey, List<StockPolicy>> professionPolicies) {
            Map<VillagerProfessionKey, Projection> byProfession = new LinkedHashMap<>();
            professionPolicies.forEach((profession, policies) ->
                    byProfession.put(profession, Projection.merge(sharedPolicies, policies)));

            return new MergedView(sharedPolicies, professionPolicies, Map.copyOf(byProfession),
                    Projection.merge(sharedPolicies, List.of()));
        }

    }

}
