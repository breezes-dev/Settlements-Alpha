package dev.breezes.settlements.domain.ai.naming;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerNamePoolResourceTest {

    @Test
    void decodesNonemptyPools_whenShippedFilesAreDiscovered() throws IOException, URISyntaxException {
        // Arrange
        URL directory = getClass().getClassLoader().getResource("data/settlements/settlements/names/villagers");
        assertNotNull(directory, "Missing shipped villager name pool directory");
        List<Path> files;
        try (Stream<Path> discovered = Files.walk(Path.of(directory.toURI()))) {
            files = discovered.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertFalse(files.isEmpty(), "No shipped villager name pool files were discovered");

        for (Path file : files) {
            // Act
            JsonElement json;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader);
            }
            DataResult<VillagerNamePool> decoded = VillagerNamePoolCodec.CODEC.parse(JsonOps.INSTANCE, json);

            // Assert
            assertTrue(decoded.result().isPresent(), () -> "Could not decode " + file + ": " + decoded.error());
            assertFalse(decoded.result().orElseThrow().names().isEmpty(), () -> "Empty shipped name pool: " + file);
        }
    }

}
