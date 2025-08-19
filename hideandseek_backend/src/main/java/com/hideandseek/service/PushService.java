package com.hideandseek.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hideandseek.model.Game;
import com.hideandseek.store.GameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PushService {

    private static final Logger logger = LoggerFactory.getLogger(PushService.class);

    @Autowired
    private GameStore gameStore;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Expo push endpoint; can be overridden via env var
    private String getExpoPushEndpoint() {
        String env = System.getenv("EXPO_PUSH_ENDPOINT");
        return (env != null && !env.isBlank()) ? env : "https://exp.host/--/api/v2/push/send";
    }

    // Optional Expo access token (if configured)
    private Optional<String> getExpoAccessToken() {
        String token = System.getenv("EXPO_ACCESS_TOKEN");
        return (token != null && !token.isBlank()) ? Optional.of(token) : Optional.empty();
    }

    public void registerToken(String gameId, String teamId, String token) {
        if (gameId == null || teamId == null || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing gameId, teamId, or token");
        }
        gameStore.registerPushToken(gameId, teamId, token);
    }

    public Set<String> getTokens(String gameId, String teamId) {
        return gameStore.getPushTokens(gameId, teamId);
    }

    // Public helpers for game events
    public void notifyCurseApplied(String gameId, String targetTeamId, String targetTeamName) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) return;
            String title = "Curse applied";
            String body = "A curse has been applied to " + targetTeamName + ".";
            Map<String, Object> data = Map.of(
                    "event", "curse_applied",
                    "gameId", gameId,
                    "targetTeamId", targetTeamId,
                    "targetTeamName", targetTeamName
            );
            // Send to all teams (seekers and hiders)
            List<Set<String>> tokenSets = game.getTeams().stream()
                    .map(t -> gameStore.getPushTokens(gameId, t.getId()))
                    .filter(set -> set != null && !set.isEmpty())
                    .toList();
            Set<String> allTokens = tokenSets.stream().flatMap(Set::stream).collect(Collectors.toSet());
            if (!allTokens.isEmpty()) {
                sendToTokens(allTokens, title, body, data);
            }
        } catch (Exception e) {
            logger.warn("Failed to send curse notification: {}", e.toString());
        }
    }

    public void notifyClueRequested(String gameId, String targetHiderTeamId, String clueTypeId, String clueTypeName) {
        try {
            Set<String> tokens = gameStore.getPushTokens(gameId, targetHiderTeamId);
            if (tokens == null || tokens.isEmpty()) return;
            String title = "Clue requested on you";
            String body = "Heads up: '" + clueTypeName + "' was requested on your team.";
            Map<String, Object> data = Map.of(
                    "event", "clue_requested",
                    "gameId", gameId,
                    "targetTeamId", targetHiderTeamId,
                    "clueTypeId", clueTypeId,
                    "clueTypeName", clueTypeName
            );
            sendToTokens(tokens, title, body, data);
        } catch (Exception e) {
            logger.warn("Failed to send clue request notification: {}", e.toString());
        }
    }

    public void sendTestNotification(String gameId, String teamId) {
        try {
            Set<String> tokens = gameStore.getPushTokens(gameId, teamId);
            if (tokens == null || tokens.isEmpty()) {
                logger.warn("No push tokens found for game {} team {}", gameId, teamId);
                return;
            }
            String title = "Test Notification";
            String body = "This is a test notification to verify push notifications are working!";
            Map<String, Object> data = Map.of(
                    "event", "test_notification",
                    "gameId", gameId,
                    "teamId", teamId,
                    "timestamp", System.currentTimeMillis()
            );
            sendToTokens(tokens, title, body, data);
            logger.info("Sent test notification to {} tokens for game {} team {}", tokens.size(), gameId, teamId);
        } catch (Exception e) {
            logger.warn("Failed to send test notification: {}", e.toString());
        }
    }

    // Low-level sender: batches by 100 (Expo limit)
    public void sendToTokens(Set<String> tokens, String title, String body, Map<String, Object> data) {
        if (tokens == null || tokens.isEmpty()) return;
        List<String> tokenList = new ArrayList<>(tokens);
        int batchSize = 100;
        for (int i = 0; i < tokenList.size(); i += batchSize) {
            List<String> batch = tokenList.subList(i, Math.min(i + batchSize, tokenList.size()));
            sendBatch(batch, title, body, data);
        }
    }

    private void sendBatch(List<String> tokens, String title, String body, Map<String, Object> data) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (String token : tokens) {
                // Basic validation of Expo push token format
                if (token == null || token.isBlank()) continue;
                Map<String, Object> msg = new HashMap<>();
                msg.put("to", token);
                msg.put("title", title);
                msg.put("body", body);
                msg.put("sound", "default");
                if (data != null && !data.isEmpty()) msg.put("data", data);
                messages.add(msg);
            }
            if (messages.isEmpty()) return;

            String json = objectMapper.writeValueAsString(messages);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(getExpoPushEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            getExpoAccessToken().ifPresent(tok -> builder.header("Authorization", "Bearer " + tok));

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("Sent push batch ({} tokens).", messages.size());
            } else {
                logger.warn("Expo push failed: status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.warn("Error sending Expo push batch: {}", e.toString());
        }
    }
}
