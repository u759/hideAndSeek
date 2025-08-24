package com.hideandseek.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.store.GameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class LocationSnapshotLogger {
    private static final Logger log = LoggerFactory.getLogger(LocationSnapshotLogger.class);

    private final GameStore gameStore;
    private final Path baseDir;
    private final ObjectMapper mapper;

    public LocationSnapshotLogger(@Value("${events.log.baseDir:}") String baseDir,
                                  GameStore gameStore) {
        this.baseDir = resolveBaseDir(baseDir);
        this.gameStore = gameStore;
        this.mapper = new ObjectMapper();
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            log.warn("Failed to ensure base dir {}: {}", this.baseDir, e.getMessage());
        }
    }

    private Path resolveBaseDir(String configuredBaseDir) {
        try {
            if (configuredBaseDir != null && !configuredBaseDir.isBlank()) {
                return Paths.get(configuredBaseDir).toAbsolutePath().normalize();
            }
            String dataDir = System.getProperty("jboss.server.data.dir");
            Path base;
            if (dataDir != null && !dataDir.isBlank()) {
                base = Paths.get(dataDir);
            } else {
                base = Paths.get(System.getProperty("user.dir", "."));
            }
            return base.resolve("hideandseek").resolve("logs").resolve("events").toAbsolutePath().normalize();
        } catch (Exception e) {
            return Paths.get("logs").resolve("events").toAbsolutePath().normalize();
        }
    }

    private Path gameDir(String gameId) {
        Path dir = baseDir.resolve(gameId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Failed to ensure game dir {}: {}", dir, e.getMessage());
        }
        return dir;
    }

    @Scheduled(fixedRate = 5000, initialDelay = 5000)
    public void snapshotAllGames() {
        try {
            long now = System.currentTimeMillis();
            String nowFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(now));
            List<Game> games = gameStore.getAllGames();
            for (Game g : games) {
                // Skip games with no teams
                if (g.getTeams() == null || g.getTeams().isEmpty()) continue;
                Path file = gameDir(g.getId()).resolve("locations.readable.ndjson");

                for (Team t : g.getTeams()) {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("time", nowFmt);
                    if (g.getCode() != null) line.put("gameCode", g.getCode());
                    line.put("team", t.getName() != null ? t.getName() : t.getId());
                    line.put("role", t.getRole());
                    if (t.getLocation() != null) {
                        line.put("latitude", t.getLocation().getLatitude());
                        line.put("longitude", t.getLocation().getLongitude());
                        long ts = t.getLocation().getTimestamp();
                        line.put("lastUpdate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ts)));
                    } else {
                        line.put("hasLocation", false);
                    }

                    writeNdjson(file, line);
                }
            }
        } catch (Exception e) {
            log.debug("Location snapshot failed: {}", e.getMessage());
        }
    }

    private void writeNdjson(Path file, Map<String, Object> line) {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.toFile(), true), StandardCharsets.UTF_8))) {
            String json = mapper.writeValueAsString(line);
            w.write(json);
            w.write("\n");
        } catch (IOException e) {
            log.warn("Failed writing location snapshot to {}: {}", file, e.getMessage());
        }
    }

    public java.io.File getReadableLocationsFile(String gameId) {
        Path dir = gameDir(gameId);
        Path file = dir.resolve("locations.readable.ndjson");
        return file.toFile();
    }
}
