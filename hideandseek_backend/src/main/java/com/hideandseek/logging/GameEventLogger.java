package com.hideandseek.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hideandseek.store.GameStore;
import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GameEventLogger {
    private static final Logger log = LoggerFactory.getLogger(GameEventLogger.class);
    private static final String VERSION = "1.0";

    private final ObjectMapper mapper;
    private final Map<String, AtomicLong> seqMap = new ConcurrentHashMap<>();
    private final Path baseDir;
    private final GameStore gameStore;

    public GameEventLogger(@Value("${events.log.baseDir:logs/events}") String baseDir, GameStore gameStore) {
        this.baseDir = Paths.get(baseDir);
        this.gameStore = gameStore;
        this.mapper = new ObjectMapper();
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            log.warn("Failed to create base event log directory {}: {}", this.baseDir, e.getMessage());
        }
    }

    public synchronized void appendEvent(String gameId, String type, String actorType, String actorId, Map<String, Object> payload) {
        try {
            long seq = nextSequence(gameId);
            long nowTs = System.currentTimeMillis();
            Map<String, Object> enriched = payload == null ? new HashMap<>() : new HashMap<>(payload);
            // Enrich payload with human-readable team names, domain names, and timestamp strings
            enrichPayloadWithNames(gameId, enriched);
            enrichPayloadWithDomainNames(gameId, enriched);
            enrichPayloadWithFormattedTimestamps(enriched);

            GameEvent evt = new GameEvent(
                    VERSION,
                    generateEventId(),
                    gameId,
                    seq,
                    nowTs,
                    type,
                    actorType,
                    actorId,
                    enriched
            );
            // Add human-readable timestamp and actor name
            evt.setTimestampFormatted(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(nowTs)));
            evt.setActorName(resolveActorName(gameId, actorType, actorId));
            writeNdjson(gameId, evt);
            // Additionally write human-only readable line (IDs removed)
            writeReadableNdjson(gameId, evt, enriched);
        } catch (Exception e) {
            log.warn("Failed to append event for game {} type {}: {}", gameId, type, e.getMessage());
        }
    }

    public File getLogFile(String gameId) {
        Path dir = gameDir(gameId);
        Path file = dir.resolve("events.ndjson");
        return file.toFile();
    }

    public File getReadableLogFile(String gameId) {
        Path dir = gameDir(gameId);
        Path file = dir.resolve("events.readable.ndjson");
        return file.toFile();
    }

    private Path gameDir(String gameId) {
        Path dir = baseDir.resolve(gameId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Failed to ensure game log dir {}: {}", dir, e.getMessage());
        }
        return dir;
    }

    private void writeNdjson(String gameId, GameEvent event) throws IOException {
        Path dir = gameDir(gameId);
        Path file = dir.resolve("events.ndjson");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.toFile(), true), StandardCharsets.UTF_8))) {
            String json = mapper.writeValueAsString(event);
            w.write(json);
            w.write("\n");
        }
    }

    private long nextSequence(String gameId) {
        return seqMap.computeIfAbsent(gameId, this::loadInitialSequence).incrementAndGet();
    }

    private AtomicLong loadInitialSequence(String gameId) {
        // Initialize sequence to last seen in file, or 0 if none
        long last = 0L;
        File logFile = getLogFile(gameId);
        if (logFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                String lastLine = null;
                while ((line = br.readLine()) != null) {
                    lastLine = line;
                }
                if (lastLine != null) {
                    try {
                        GameEvent evt = mapper.readValue(lastLine, GameEvent.class);
                        last = evt.getSequence();
                    } catch (Exception ignored) { }
                }
            } catch (IOException ignored) { }
        }
        return new AtomicLong(last);
    }

    private String generateEventId() {
        // Simple ULID-like: time-based + random. Good enough for uniqueness here.
        String ts = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String rand = UUID.randomUUID().toString().replace("-", "");
        return ts + "-" + rand.substring(0, 12);
    }

    private String resolveActorName(String gameId, String actorType, String actorId) {
        try {
            if (actorId == null || actorId.isBlank()) return actorType;
            if ("team".equalsIgnoreCase(actorType)) {
                Team t = gameStore.getTeam(gameId, actorId);
                return t != null && t.getName() != null ? t.getName() : actorId;
            } else if ("admin".equalsIgnoreCase(actorType)) {
                return "Admin";
            } else if ("system".equalsIgnoreCase(actorType)) {
                return "System";
            }
            return actorId;
        } catch (Exception ignored) {
            return actorId;
        }
    }

    private void enrichPayloadWithNames(String gameId, Map<String, Object> payload) {
        try {
            if (payload == null) return;
            // Single team IDs
            addNameForKey(gameId, payload, "teamId", "teamName");
            addNameForKey(gameId, payload, "seekerTeamId", "seekerTeamName");
            addNameForKey(gameId, payload, "targetTeamId", "targetTeamName");
            addNameForKey(gameId, payload, "hiderTeamId", "hiderTeamName");
            addNameForKey(gameId, payload, "requestingTeamId", "requestingTeamName");

            // Lists of team IDs
            addNamesForList(gameId, payload, "targetHiderTeamIds", "targetHiderTeamNames");
        } catch (Exception ignored) {
        }
    }

    private void enrichPayloadWithDomainNames(String gameId, Map<String, Object> payload) {
        try {
            if (payload == null) return;
            // challengeId -> challengeTitle
            Object ch = payload.get("challengeId");
            if (ch instanceof String s && !payload.containsKey("challengeTitle")) {
                String title = resolveChallengeTitle(s);
                if (title != null) payload.put("challengeTitle", title);
            }
            // clueTypeId -> clueTypeName
            Object ct = payload.get("clueTypeId");
            if (ct instanceof String s && !payload.containsKey("clueTypeName")) {
                String name = resolveClueTypeName(s);
                if (name != null) payload.put("clueTypeName", name);
            }
            // curseId -> curseTitle
            Object cu = payload.get("curseId");
            if (cu instanceof String s && !payload.containsKey("curseTitle")) {
                String title = resolveCurseTitle(s);
                if (title != null) payload.put("curseTitle", title);
            }
        } catch (Exception ignored) {
        }
    }

    private void enrichPayloadWithFormattedTimestamps(Map<String, Object> payload) {
        try {
            if (payload == null || payload.isEmpty()) return;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (Map.Entry<String, Object> e : new ArrayList<>(payload.entrySet())) {
                String k = e.getKey();
                Object v = e.getValue();
                if (v instanceof Number) {
                    long millis = ((Number) v).longValue();
                    if (k.endsWith("Time") || k.endsWith("At") || k.equalsIgnoreCase("timestamp")) {
                        String fmtKey = k + "Formatted";
                        if (!payload.containsKey(fmtKey)) {
                            payload.put(fmtKey, fmt.format(new Date(millis)));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void addNamesForList(String gameId, Map<String, Object> payload, String idsKey, String namesKey) {
        Object v = payload.get(idsKey);
        if (v instanceof Collection<?>) {
            List<String> names = new ArrayList<>();
            for (Object o : (Collection<?>) v) {
                if (o instanceof String s) {
                    names.add(resolveTeamName(gameId, s));
                }
            }
            if (!names.isEmpty()) {
                payload.put(namesKey, names);
            }
        }
    }

    private void addNameForKey(String gameId, Map<String, Object> payload, String idKey, String nameKey) {
        Object v = payload.get(idKey);
        if (v instanceof String s && !payload.containsKey(nameKey)) {
            payload.put(nameKey, resolveTeamName(gameId, s));
        }
    }

    private String resolveTeamName(String gameId, String teamId) {
        try {
            Team t = gameStore.getTeam(gameId, teamId);
            return t != null && t.getName() != null ? t.getName() : teamId;
        } catch (Exception ignored) {
            return teamId;
        }
    }

    private String resolveChallengeTitle(String challengeId) {
        try {
            for (var c : gameStore.getAllChallenges()) {
                if (challengeId.equals(c.getId())) return c.getTitle();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveClueTypeName(String clueTypeId) {
        try {
            var ct = gameStore.getClueTypeById(clueTypeId);
            return ct != null ? ct.getName() : null;
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveCurseTitle(String curseId) {
        try {
            for (var c : gameStore.getAllCurses()) {
                if (curseId.equals(c.getId())) return c.getTitle();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void writeReadableNdjson(String gameId, GameEvent evt, Map<String, Object> enrichedPayload) throws IOException {
        // Build human-only map: keep names and formatted times, remove IDs
        Map<String, Object> human = new LinkedHashMap<>();
        human.put("time", evt.getTimestampFormatted());
        human.put("type", evt.getType());
        human.put("actor", evt.getActorName());
        Game g = gameStore.getGame(gameId);
        if (g != null && g.getCode() != null) human.put("gameCode", g.getCode());

        // Sanitize payload: remove keys named 'id' or ending with 'Id'
        Map<String, Object> details = new LinkedHashMap<>();
        if (enrichedPayload != null) {
            for (Map.Entry<String, Object> e : enrichedPayload.entrySet()) {
                String k = e.getKey();
                if ("id".equalsIgnoreCase(k) || k.endsWith("Id")) continue;
                // Prefer formatted timestamps
                if (k.equalsIgnoreCase("timestamp") || k.endsWith("Time") || k.endsWith("At")) {
                    // Skip raw time fields; rely on *Formatted siblings
                    continue;
                }
                details.put(k, e.getValue());
            }
            // Add any formatted timestamp siblings (keys ending with 'Formatted')
            for (Map.Entry<String, Object> e : enrichedPayload.entrySet()) {
                String k = e.getKey();
                if (k.endsWith("Formatted")) {
                    details.put(k, e.getValue());
                }
            }
        }
        if (!details.isEmpty()) human.put("details", details);

        Path dir = gameDir(gameId);
        Path file = dir.resolve("events.readable.ndjson");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.toFile(), true), StandardCharsets.UTF_8))) {
            String json = mapper.writeValueAsString(human);
            w.write(json);
            w.write("\n");
        }
    }
}
