package dev.breezes.settlements.domain.animation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerAnimationObservationTest {

    @ParameterizedTest
    @CsvSource({
            "NaN, 0.0",
            "-0.25, 0.0",
            "-Infinity, 0.0",
            "1.5, 1.0",
            "Infinity, 1.0",
            "0.75, 0.75"})
    void setSampleKey_takesAPartialTickOutsideTheTick_asItsNearestEnd(float written, float kept) {
        // Arrange
        VillagerAnimationObservation observation = new VillagerAnimationObservation();

        // Act
        observation.setSampleKey(10L, written);

        // Assert
        assertEquals(kept, observation.getPartialTicks(), 0.0F);
        assertEquals(10L, observation.getAgeTicks());
    }

}
