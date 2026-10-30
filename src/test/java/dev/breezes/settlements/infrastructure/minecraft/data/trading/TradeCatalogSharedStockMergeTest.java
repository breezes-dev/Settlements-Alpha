package dev.breezes.settlements.infrastructure.minecraft.data.trading;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.breezes.settlements.domain.economy.catalog.DemandEntry;
import dev.breezes.settlements.domain.economy.catalog.ItemMatch;
import dev.breezes.settlements.domain.economy.catalog.OfferEntry;
import dev.breezes.settlements.domain.economy.catalog.StockPolicy;
import dev.breezes.settlements.domain.economy.catalog.SupplyEntry;
import dev.breezes.settlements.domain.entities.VillagerProfessionKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeCatalogSharedStockMergeTest {

    private static final VillagerProfessionKey FILLER_PROFESSION = new VillagerProfessionKey(ResourceLocation.parse("filler:profession"));
    private static final VillagerProfessionKey FILELESS_PROFESSION = new VillagerProfessionKey(ResourceLocation.parse("filler:fileless"));

    private final SharedStockDataManager sharedStock = new SharedStockDataManager();
    private final TradeCatalogDataManager catalog = new TradeCatalogDataManager(this.sharedStock);

    @Test
    void professionEntry_replacesSharedEntryWithSameId() {
        this.loadShared("a", restockOnly("filler_item", "filler:one"));
        this.loadProfession("filler", FILLER_PROFESSION, offerOnly("filler_item", "filler:one"));

        List<StockPolicy> policies = this.catalog.stockPoliciesFor(FILLER_PROFESSION);

        // Counterexample: an override that duplicates would leave two policies and keep the shared restock demand
        assertEquals(1, policies.size());
        assertTrue(this.catalog.demandsFor(FILLER_PROFESSION).isEmpty());
        assertEquals(1, this.catalog.offersFor(FILLER_PROFESSION).size());
    }

    @Test
    void professionWithoutCatalogFile_getsSharedEntriesAlone() {
        this.loadShared("a", restockOnly("filler_item", "filler:one"));
        this.loadProfession("filler", FILLER_PROFESSION, offerOnly("filler_other", "filler:two"));

        // Counterexample: a fileless profession answered with getOrDefault(..., List.of()) would be empty
        assertEquals(List.of("filler_item"), ids(this.catalog.stockPoliciesFor(FILELESS_PROFESSION)));
        assertEquals(1, this.catalog.demandsFor(FILELESS_PROFESSION).size());
        assertEquals(1, this.catalog.findDemands(FILELESS_PROFESSION, new ItemMatch.ItemRef(ResourceLocation.parse("filler:one"))).size());
    }

    @Test
    void mergedOrder_isSharedEntriesThenProfessionOnlyEntries_withOverrideKeepingSharedPosition() {
        this.loadShared("a", restockOnly("shared_first", "filler:one"), restockOnly("shared_second", "filler:two"));
        this.loadProfession("filler", FILLER_PROFESSION,
                offerOnly("profession_only", "filler:three"), offerOnly("shared_first", "filler:one"));

        assertEquals(List.of("shared_first", "shared_second", "profession_only"),
                ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));
    }

    @Test
    void projections_includeSharedEntriesOfEveryFacet() {
        this.loadShared("a",
                restockOnly("restock_filler", "filler:one"),
                offerOnly("offer_filler", "filler:two"),
                dumpOnly("dump_filler", "filler:three"));

        List<DemandEntry> demands = this.catalog.demandsFor(FILLER_PROFESSION);
        List<OfferEntry> offers = this.catalog.offersFor(FILLER_PROFESSION);
        List<SupplyEntry> supply = this.catalog.supplyFor(FILLER_PROFESSION);

        assertEquals(List.of("restock_filler"), demands.stream().map(DemandEntry::id).toList());
        assertEquals(List.of("offer_filler"), offers.stream().map(OfferEntry::id).toList());
        assertEquals(List.of("dump_filler"), supply.stream().map(SupplyEntry::id).toList());
        assertEquals(1, this.catalog.findOffers(FILLER_PROFESSION, new ItemMatch.ItemRef(ResourceLocation.parse("filler:two"))).size());
    }

    @Test
    void laterSharedFile_replacesEarlierSharedFileEntryById() {
        this.sharedStock.reload(Map.of(
                ResourceLocation.parse("settlements:shared_stock/a"), stock(restockOnly("filler_item", "filler:one")),
                ResourceLocation.parse("settlements:shared_stock/b"), stock(offerOnly("filler_item", "filler:one"))));

        assertEquals(1, this.catalog.stockPoliciesFor(FILLER_PROFESSION).size());
        assertTrue(this.catalog.demandsFor(FILLER_PROFESSION).isEmpty());
    }

    @Test
    void sharedFileThatFailsToParse_isSkippedAndOtherSharedFilesStillApply() {
        this.sharedStock.reload(Map.of(
                ResourceLocation.parse("settlements:shared_stock/a"), stock(restockOnly("good_filler", "filler:one")),
                ResourceLocation.parse("settlements:shared_stock/b"), JsonParser.parseString("""
                        { "stock": [ { "id": "bad_filler", "match": { "item": "filler:two" } } ] }
                        """)));

        assertEquals(List.of("good_filler"), ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));
    }

    @Test
    void mergedView_doesNotDependOnWhichSourceReloadsFirst() {
        Map<ResourceLocation, JsonElement> sharedEntries = Map.of(
                ResourceLocation.parse("settlements:shared_stock/a"), stock(restockOnly("shared_filler", "filler:one")));
        Map<ResourceLocation, JsonElement> professionEntries = Map.of(
                ResourceLocation.parse("settlements:trade_catalog/filler"),
                professionFile(FILLER_PROFESSION, offerOnly("profession_filler", "filler:two")));

        this.sharedStock.reload(sharedEntries);
        this.catalog.reload(professionEntries);

        SharedStockDataManager otherShared = new SharedStockDataManager();
        TradeCatalogDataManager otherCatalog = new TradeCatalogDataManager(otherShared);
        otherCatalog.reload(professionEntries);
        otherShared.reload(sharedEntries);

        List<String> expected = List.of("shared_filler", "profession_filler");
        assertEquals(expected, ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));
        assertEquals(expected, ids(otherCatalog.stockPoliciesFor(FILLER_PROFESSION)));
    }

    @Test
    void sharedStockReloadedAfterFirstRead_isVisibleOnNextRead() {
        this.loadShared("a", restockOnly("old_filler", "filler:one"));
        assertEquals(List.of("old_filler"), ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));

        this.loadShared("a", restockOnly("new_filler", "filler:two"));

        assertEquals(List.of("new_filler"), ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));
    }

    @Test
    void professionCatalogReloadedAfterFirstRead_isVisibleOnNextRead() {
        this.loadProfession("filler", FILLER_PROFESSION, offerOnly("old_filler", "filler:one"));
        assertEquals(List.of("old_filler"), ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));

        this.loadProfession("filler", FILLER_PROFESSION, offerOnly("new_filler", "filler:two"));

        assertEquals(List.of("new_filler"), ids(this.catalog.stockPoliciesFor(FILLER_PROFESSION)));
    }

    @Test
    void shippedData_toolsmithLanternOverridesSharedRestock() throws IOException {
        this.loadShippedData();
        VillagerProfessionKey toolsmith = vanilla("toolsmith");

        long lanternPolicyCount = this.catalog.stockPoliciesFor(toolsmith).stream()
                .filter(policy -> policy.id().equals("lantern"))
                .count();

        assertEquals(1, lanternPolicyCount);
        assertFalse(this.catalog.demandsFor(toolsmith).stream().anyMatch(demand -> demand.id().equals("lantern")));
        assertTrue(this.catalog.offersFor(toolsmith).stream().anyMatch(offer -> offer.id().equals("lantern")));
    }

    @Test
    void shippedData_professionWithoutOverride_restocksLanternAndFood() throws IOException {
        this.loadShippedData();

        for (VillagerProfessionKey profession : List.of(vanilla("cleric"), FILELESS_PROFESSION)) {
            List<String> demandIds = this.catalog.demandsFor(profession).stream().map(DemandEntry::id).toList();
            assertTrue(demandIds.contains("lantern"), profession + " should restock lanterns");
            assertTrue(demandIds.contains("food"), profession + " should restock food");
        }
    }

    @Test
    void shippedData_fletcherAndFarmerKeepTheirOwnOverrides() throws IOException {
        this.loadShippedData();

        assertTrue(this.catalog.offersFor(vanilla("fletcher")).stream().anyMatch(offer -> offer.id().equals("ballista_bolt")));
        assertFalse(this.catalog.demandsFor(vanilla("fletcher")).stream().anyMatch(demand -> demand.id().equals("ballista_bolt")));
        assertTrue(this.catalog.offersFor(vanilla("farmer")).stream().anyMatch(offer -> offer.id().equals("food")));
    }

    @Test
    void shippedData_clericSoulLanternIsDumpedButNeverOffered() throws IOException {
        this.loadShippedData();
        VillagerProfessionKey cleric = vanilla("cleric");

        assertTrue(this.catalog.supplyFor(cleric).stream().anyMatch(supply -> supply.id().equals("soul_lantern")));
        assertFalse(this.catalog.offersFor(cleric).stream().anyMatch(offer -> offer.id().equals("soul_lantern")));
        assertFalse(this.catalog.demandsFor(cleric).stream().anyMatch(demand -> demand.id().equals("soul_lantern")));
    }

    private void loadShippedData() throws IOException {
        this.sharedStock.reload(Map.of(
                ResourceLocation.parse("settlements:shared_stock/villager_essentials"),
                readShipped("shared_stock/villager_essentials")));
        this.catalog.reload(Map.of(
                ResourceLocation.parse("settlements:trade_catalog/toolsmith"), readShipped("trade_catalog/toolsmith"),
                ResourceLocation.parse("settlements:trade_catalog/cleric"), readShipped("trade_catalog/cleric"),
                ResourceLocation.parse("settlements:trade_catalog/fletcher"), readShipped("trade_catalog/fletcher"),
                ResourceLocation.parse("settlements:trade_catalog/farmer"), readShipped("trade_catalog/farmer")));
    }

    private static JsonElement readShipped(String path) throws IOException {
        String resourcePath = "data/settlements/settlements/" + path + ".json";
        try (InputStream stream = TradeCatalogSharedStockMergeTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing resource: " + resourcePath);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        }
    }

    private void loadShared(String fileName, String... policyJson) {
        this.sharedStock.reload(Map.of(ResourceLocation.parse("settlements:shared_stock/" + fileName), stock(policyJson)));
    }

    private void loadProfession(String fileName, VillagerProfessionKey profession, String... policyJson) {
        this.catalog.reload(Map.of(ResourceLocation.parse("settlements:trade_catalog/" + fileName), professionFile(profession, policyJson)));
    }

    private static JsonElement stock(String... policyJson) {
        return JsonParser.parseString("{ \"stock\": [" + String.join(",", policyJson) + "] }");
    }

    private static JsonElement professionFile(VillagerProfessionKey profession, String... policyJson) {
        return JsonParser.parseString("{ \"profession\": \"" + profession.id() + "\", \"stock\": [" + String.join(",", policyJson) + "] }");
    }

    private static String restockOnly(String id, String item) {
        return "{ \"id\": \"" + id + "\", \"match\": { \"item\": \"" + item + "\" },"
                + " \"restock\": { \"below\": 2, \"buyPricePerUnit\": 1, \"priority\": 5 } }";
    }

    private static String offerOnly(String id, String item) {
        return "{ \"id\": \"" + id + "\", \"match\": { \"item\": \"" + item + "\" },"
                + " \"offer\": { \"above\": 3, \"basePrice\": 4, \"priceJitter\": 0, \"bundleSize\": 1 } }";
    }

    private static String dumpOnly(String id, String item) {
        return "{ \"id\": \"" + id + "\", \"match\": { \"item\": \"" + item + "\" }, \"dump\": { \"above\": 9 } }";
    }

    private static List<String> ids(List<StockPolicy> policies) {
        return policies.stream().map(StockPolicy::id).toList();
    }

    private static VillagerProfessionKey vanilla(String name) {
        return new VillagerProfessionKey(ResourceLocation.withDefaultNamespace(name));
    }

}
