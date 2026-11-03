package dev.breezes.settlements.domain.ai.naming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerNameTableTest {

    private static final UUID VILLAGER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private VillagerNameTable table;

    @BeforeEach
    void setUp() {
        this.table = new VillagerNameTable();
    }

    @Test
    void returnsGeneratedName_whenUuidHasNoStoredName() {
        // Arrange
        VillagerNamePool pool = new VillagerNamePool(List.of("Generated name"));

        // Act
        String resolved = this.table.resolve(VILLAGER_ID, pool);

        // Assert
        assertEquals("Generated name", resolved);
        assertTrue(this.table.entriesView().isEmpty());
        assertFalse(this.table.isDirty());
    }

    @Test
    void returnsStoredName_whenThePoolIsEmpty() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Alderman Finn");

        // Act
        String resolved = this.table.resolve(VILLAGER_ID, VillagerNamePool.EMPTY);

        // Assert
        assertEquals("Alderman Finn", resolved);
    }

    @Test
    void marksDirty_whenANewNameIsStored() {
        // Arrange

        // Act
        this.table.upsert(VILLAGER_ID, "Finn");

        // Assert
        assertTrue(this.table.isDirty());
    }

    @Test
    void staysClean_whenTheSameNameIsStoredAgain() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Finn");
        this.table.clearDirty();

        // Act
        this.table.upsert(VILLAGER_ID, "Finn");

        // Assert
        assertFalse(this.table.isDirty());
    }

    @Test
    void marksDirtyAndReturnsTheReplacement_whenAStoredNameChanges() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Finn");
        this.table.clearDirty();

        // Act
        this.table.upsert(VILLAGER_ID, "Renamed Finn");

        // Assert
        assertTrue(this.table.isDirty());
        assertEquals("Renamed Finn", this.table.resolve(VILLAGER_ID, VillagerNamePool.EMPTY));
    }

    @Test
    void marksDirtyAndUsesTheCurrentPool_whenAStoredNameIsRemoved() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Finn");
        this.table.clearDirty();
        VillagerNamePool pool = new VillagerNamePool(List.of("Fallback name"));

        // Act
        this.table.remove(VILLAGER_ID);

        // Assert
        assertTrue(this.table.isDirty());
        assertEquals("Fallback name", this.table.resolve(VILLAGER_ID, pool));
    }

    @Test
    void staysClean_whenAnAbsentNameIsRemoved() {
        // Arrange
        this.table.upsert(OTHER_ID, "Someone Else");
        this.table.clearDirty();

        // Act
        this.table.remove(VILLAGER_ID);

        // Assert
        assertFalse(this.table.isDirty());
    }

    @Test
    void staysClean_afterDirtyStateIsCleared() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Finn");

        // Act
        this.table.clearDirty();

        // Assert
        assertFalse(this.table.isDirty());
    }

    @Test
    void retainsRestoredNames_withoutMarkingDirty() {
        // Arrange
        Map<UUID, String> restoredNames = Map.of(VILLAGER_ID, "Finn");

        // Act
        VillagerNameTable seeded = new VillagerNameTable(restoredNames);

        // Assert
        assertFalse(seeded.isDirty());
        assertEquals("Finn", seeded.resolve(VILLAGER_ID, VillagerNamePool.EMPTY));
    }

    @Test
    void containsCurrentNames_afterAnEntryIsRemoved() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Finn");
        this.table.upsert(OTHER_ID, "Astrid");
        this.table.remove(OTHER_ID);

        // Act
        Map<UUID, String> entries = this.table.entriesView();

        // Assert
        assertEquals(1, entries.size());
        assertEquals("Finn", entries.get(VILLAGER_ID));
    }

    @Test
    void usesTheNewPoolWithoutStoringFallbacks_whenThePoolChanges() {
        // Arrange
        VillagerNamePool previousPool = new VillagerNamePool(List.of("Before reload"));
        VillagerNamePool currentPool = new VillagerNamePool(List.of("After reload"));

        // Act
        String previousName = this.table.resolve(VILLAGER_ID, previousPool);
        String currentName = this.table.resolve(VILLAGER_ID, currentPool);

        // Assert
        assertEquals("Before reload", previousName);
        assertEquals("After reload", currentName);
        assertTrue(this.table.entriesView().isEmpty());
        assertFalse(this.table.isDirty());
    }

    @Test
    void keepsTheStoredName_whenThePoolChanges() {
        // Arrange
        this.table.upsert(VILLAGER_ID, "Player's name");
        this.table.clearDirty();
        VillagerNamePool previousPool = new VillagerNamePool(List.of("Before reload"));
        VillagerNamePool currentPool = new VillagerNamePool(List.of("After reload"));

        // Act
        String previousName = this.table.resolve(VILLAGER_ID, previousPool);
        String currentName = this.table.resolve(VILLAGER_ID, currentPool);

        // Assert
        assertEquals("Player's name", previousName);
        assertEquals("Player's name", currentName);
        assertFalse(this.table.isDirty());
    }

    @Test
    void returnsUuidIdentityWithoutStoringIt_whenNoPoolOrStoredNameIsAvailable() {
        // Arrange
        VillagerNamePool pool = VillagerNamePool.EMPTY;

        // Act
        String resolved = this.table.resolve(VILLAGER_ID, pool);

        // Assert
        assertEquals(VILLAGER_ID.toString(), resolved);
        assertTrue(this.table.entriesView().isEmpty());
        assertFalse(this.table.isDirty());
    }

}
