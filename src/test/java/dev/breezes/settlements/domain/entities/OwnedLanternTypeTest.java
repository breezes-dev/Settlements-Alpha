package dev.breezes.settlements.domain.entities;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnedLanternTypeTest {

    @ParameterizedTest
    @MethodSource("unknownNetworkBytes")
    void unknownNetworkByte_decodesToNone(byte unknownVariant) {
        // Act
        OwnedLanternType actual = OwnedLanternType.fromNetworkByte(unknownVariant);

        // Assert
        assertEquals(OwnedLanternType.NONE, actual);
    }

    private static Stream<Byte> unknownNetworkBytes() {
        // The first byte past the last variant, then the edges of the signed and unsigned byte ranges
        return Stream.of((byte) OwnedLanternType.values().length, Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) -1);
    }

}
