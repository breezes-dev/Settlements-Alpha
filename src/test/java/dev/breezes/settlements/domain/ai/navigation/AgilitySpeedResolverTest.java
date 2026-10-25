package dev.breezes.settlements.domain.ai.navigation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgilitySpeedResolverTest {

    private static final float DELTA = 0.0001F;
    private static final float MIDPOINT_OFFSET = 0.001F;

    @Test
    void resolve_returnsBaseModifierWhenAgilityIsCentered() {
        float result = AgilitySpeedResolver.resolve(NavigationType.WALK, 0.5);

        assertEquals(0.5F, result, DELTA);
    }

    @Test
    void resolve_appliesPositiveAgilityImpactAtMaximumAgility() {
        float result = AgilitySpeedResolver.resolve(NavigationType.RUN, 1.0);

        assertEquals(0.84F, result, DELTA);
    }

    @Test
    void resolve_appliesNegativeAgilityImpactAtMinimumAgility() {
        float result = AgilitySpeedResolver.resolve(NavigationType.SPRINT, 0.0);

        assertEquals(0.72F, result, DELTA);
    }

    @Test
    void resolve_neverReturnsLessThanSafetyFloor() {
        float result = AgilitySpeedResolver.resolve(NavigationType.STROLL, -10.0);

        assertEquals(0.1F, result, DELTA);
    }

    @ParameterizedTest
    @MethodSource("speedDerivableTypesAtAgilities")
    void resolveNearestNavigationType_returnsType_whenSpeedIsThatTypesSpeedAtTheSameAgility(NavigationType type,
                                                                                           double agility) {
        // Arrange
        float speedModifier = AgilitySpeedResolver.resolve(type, agility);

        // Act
        NavigationType result = AgilitySpeedResolver.resolveNearestNavigationType(speedModifier, agility);

        // Assert
        assertEquals(type, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 0.5, 0.9})
    void resolveNearestNavigationType_returnsFasterType_whenSpeedIsJustAboveMidpoint(double agility) {
        // Arrange
        float midpoint = (AgilitySpeedResolver.resolve(NavigationType.WALK, agility)
                + AgilitySpeedResolver.resolve(NavigationType.RUN, agility)) / 2.0F;

        // Act
        NavigationType result = AgilitySpeedResolver.resolveNearestNavigationType(midpoint + MIDPOINT_OFFSET, agility);

        // Assert
        assertEquals(NavigationType.RUN, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 0.5, 0.9})
    void resolveNearestNavigationType_returnsSlowerType_whenSpeedIsJustBelowMidpoint(double agility) {
        // Arrange
        float midpoint = (AgilitySpeedResolver.resolve(NavigationType.WALK, agility)
                + AgilitySpeedResolver.resolve(NavigationType.RUN, agility)) / 2.0F;

        // Act
        NavigationType result = AgilitySpeedResolver.resolveNearestNavigationType(midpoint - MIDPOINT_OFFSET, agility);

        // Assert
        assertEquals(NavigationType.WALK, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 0.5, 0.9})
    void resolveNearestNavigationType_neverReturnsPanic_whenSpeedIsPanicSpeed(double agility) {
        // Arrange
        float speedModifier = AgilitySpeedResolver.resolve(NavigationType.PANIC, agility);

        // Act
        NavigationType result = AgilitySpeedResolver.resolveNearestNavigationType(speedModifier, agility);

        // Assert
        assertNotEquals(NavigationType.PANIC, result);
    }

    private static Stream<Arguments> speedDerivableTypesAtAgilities() {
        return Arrays.stream(NavigationType.values())
                .filter(type -> type != NavigationType.PANIC)
                .flatMap(type -> Stream.of(0.1, 0.5, 0.9).map(agility -> Arguments.of(type, agility)));
    }

}
