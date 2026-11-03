package dev.breezes.settlements.domain.ai.naming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerNamePoolTest {

    @Test
    void stripsNamesAndDropsBlanks_whenPoolIsCreated() {
        // Arrange
        List<String> names = List.of("  River  ", " ", "\t\n", "\u2003Sky\u2003");

        // Act
        VillagerNamePool pool = new VillagerNamePool(names);

        // Assert
        assertEquals(List.of("River", "Sky"), pool.names());
    }

    @Test
    void keepsFirstSpellingAndOrder_whenNamesDifferOnlyInCase() {
        // Arrange
        List<String> names = List.of(" River ", "SKY", "river", "sky", "RIVER", "Moss");

        // Act
        VillagerNamePool pool = new VillagerNamePool(names);

        // Assert
        assertEquals(List.of("River", "SKY", "Moss"), pool.names());
    }

    @Test
    void preservesSnapshot_whenSourceListChanges() {
        // Arrange
        List<String> names = new ArrayList<>(List.of("River", "Sky"));
        VillagerNamePool pool = new VillagerNamePool(names);

        // Act
        names.clear();
        names.add("Moss");

        // Assert
        assertEquals(List.of("River", "Sky"), pool.names());
    }

    @Test
    void rejectsMutation_whenNamesAreRead() {
        // Arrange
        VillagerNamePool pool = new VillagerNamePool(List.of("River"));

        // Act + Assert
        assertThrows(UnsupportedOperationException.class, () -> pool.names().add("Sky"));
    }

    @Test
    void allowsEmptyPool_whenNamesContainNoNonblankEntries() {
        // Arrange
        List<String> names = List.of(" ", "\t\n");

        // Act
        VillagerNamePool pool = new VillagerNamePool(names);

        // Assert
        assertTrue(pool.names().isEmpty());
    }

    @Test
    void rejectsNullCollection_whenPoolIsCreated() {
        // Act + Assert
        assertThrows(NullPointerException.class, () -> new VillagerNamePool(null));
    }

    @Test
    void rejectsNullName_whenPoolIsCreated() {
        // Arrange
        List<String> names = Arrays.asList("River", null);

        // Act + Assert
        assertThrows(NullPointerException.class, () -> new VillagerNamePool(names));
    }

    @Test
    void selectsSameName_whenUuidValueIsRepeatedAfterOtherCalls() {
        // Arrange
        VillagerNamePool pool = new VillagerNamePool(List.of("first", "second", "third"));
        UUID id = new UUID(1234L, -5678L);
        UUID equalId = new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits());
        UUID otherId = new UUID(-4321L, 8765L);

        // Act
        String first = pool.selectNameForUuid(id).orElseThrow();
        pool.selectNameForUuid(otherId);
        String second = pool.selectNameForUuid(equalId).orElseThrow();

        // Assert
        assertEquals(first, second);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE})
    void selectsPoolNames_whenEitherUuidHalfHasASignedBoundary(long varyingHalf) {
        // Arrange
        VillagerNamePool pool = new VillagerNamePool(List.of("first", "second", "third"));
        UUID varyingMostSignificantBits = new UUID(varyingHalf, 0L);
        UUID varyingLeastSignificantBits = new UUID(0L, varyingHalf);

        // Act
        String nameFromMostSignificantBits = pool.selectNameForUuid(varyingMostSignificantBits).orElseThrow();
        String nameFromLeastSignificantBits = pool.selectNameForUuid(varyingLeastSignificantBits).orElseThrow();

        // Assert
        assertTrue(pool.names().contains(nameFromMostSignificantBits));
        assertTrue(pool.names().contains(nameFromLeastSignificantBits));
    }

    @Test
    void variesSelectedNames_whenMostSignificantBitsChangeAndLeastSignificantBitsStayFixed() {
        // Arrange
        VillagerNamePool pool = new VillagerNamePool(List.of("first", "second", "third"));
        int sampleSize = 64;
        long leastSignificantBits = 42L;

        // Act
        Set<String> names = LongStream.range(0, sampleSize)
                .mapToObj(mostSignificantBits -> new UUID(mostSignificantBits, leastSignificantBits))
                .map(uuid -> pool.selectNameForUuid(uuid).orElseThrow())
                .collect(Collectors.toSet());

        // Assert
        assertTrue(names.size() > 1, "Changing the most significant bits must affect name selection");
    }

    @Test
    void variesSelectedNames_whenLeastSignificantBitsChangeAndMostSignificantBitsStayFixed() {
        // Arrange
        VillagerNamePool pool = new VillagerNamePool(List.of("first", "second", "third"));
        int sampleSize = 64;
        long mostSignificantBits = 42L;

        // Act
        Set<String> names = LongStream.range(0, sampleSize)
                .mapToObj(leastSignificantBits -> new UUID(mostSignificantBits, leastSignificantBits))
                .map(uuid -> pool.selectNameForUuid(uuid).orElseThrow())
                .collect(Collectors.toSet());

        // Assert
        assertTrue(names.size() > 1, "Changing the least significant bits must affect name selection");
    }

    @Test
    void selectsNoName_whenPoolHasNoNames() {
        // Arrange
        VillagerNamePool pool = VillagerNamePool.EMPTY;
        UUID id = new UUID(Long.MIN_VALUE, Long.MAX_VALUE);

        // Act
        Optional<String> name = pool.selectNameForUuid(id);

        // Assert
        assertTrue(name.isEmpty());
    }

}
