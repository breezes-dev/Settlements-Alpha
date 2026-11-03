package dev.breezes.settlements.domain.ai.naming;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerNamePoolCodecTest {

    @Test
    void normalizesNames_whenRequiredArrayIsDecoded() {
        // Arrange
        JsonElement json = JsonParser.parseString("""
                {"names": [" River ", "river", "", "Sky"]}
                """);

        // Act
        VillagerNamePool pool = VillagerNamePoolCodec.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        // Assert
        assertEquals(new VillagerNamePool(List.of("River", "Sky")), pool);
    }

    @Test
    void acceptsEmptyPool_whenNamesArrayIsEmpty() {
        // Arrange
        JsonElement json = JsonParser.parseString("""
                {"names": []}
                """);

        // Act
        VillagerNamePool pool = VillagerNamePoolCodec.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        // Assert
        assertTrue(pool.names().isEmpty());
    }

    @Test
    void rejectsFile_whenNamesArrayIsMissing() {
        // Arrange
        JsonElement json = JsonParser.parseString("{}");

        // Act
        DataResult<VillagerNamePool> decoded = VillagerNamePoolCodec.CODEC.parse(JsonOps.INSTANCE, json);

        // Assert
        assertTrue(decoded.result().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"names\": null}",
            "{\"names\": \"River\"}",
            "{\"names\": [\"River\", null]}",
            "{\"names\": [\"River\", {}]}",
            "{\"names\": [\"River\", []]}"
    })
    void rejectsFile_whenNamesArrayIsMalformed(String document) {
        // Arrange
        JsonElement json = JsonParser.parseString(document);

        // Act
        DataResult<VillagerNamePool> decoded = VillagerNamePoolCodec.CODEC.parse(JsonOps.INSTANCE, json);

        // Assert
        assertTrue(decoded.result().isEmpty());
    }

}
