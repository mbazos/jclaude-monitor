package com.mbazos.jclaude.service;

import com.mbazos.jclaude.model.LocalStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileIsEmpty() {
        LocalDataReader reader = new LocalDataReader(tempDir.resolve("nope.json"));
        assertTrue(reader.readStats().isEmpty());
    }

    @Test
    void validFileIsPresent() throws Exception {
        Path file = tempDir.resolve("stats-cache.json");
        Files.writeString(file, "{\"totalSessions\":479,\"totalMessages\":12034}");
        Optional<LocalStats> stats = new LocalDataReader(file).readStats();
        assertEquals(479L, stats.orElseThrow().totalSessions());
        assertEquals(12034L, stats.orElseThrow().totalMessages());
    }

    @Test
    void garbageFileIsEmpty() throws Exception {
        Path file = tempDir.resolve("stats-cache.json");
        Files.writeString(file, "not json at all");
        assertTrue(new LocalDataReader(file).readStats().isEmpty());
    }

    @Test
    void unrelatedJsonIsEmpty() throws Exception {
        Path file = tempDir.resolve("stats-cache.json");
        Files.writeString(file, "{\"something\":\"else\"}");
        assertTrue(new LocalDataReader(file).readStats().isEmpty());
    }
}
