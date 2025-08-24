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
    
    public void unregisterToken(String gameId, String teamId, String token) {
        if (gameId == null || teamId == null || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing gameId, teamId, or token");
        }
        gameStore.unregisterPushToken(gameId, teamId, token);
    }
    
    public void unregisterDeviceFromAllTeams(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing token");
        }
        gameStore.unregisterAllTokensForDevice(token);
    }

    public Set<String> getTokens(String gameId, String teamId) {
        return gameStore.getPushTokens(gameId, teamId);
    }

    public void sendGameEventNotification(String gameId, String title, String body) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) {
                logger.warn("Cannot send notification: game {} not found", gameId);
                return;
            }

            // Get all push tokens for all teams in the game
            Set<String> allTokens = new HashSet<>();
            for (var team : game.getTeams()) {
                allTokens.addAll(gameStore.getPushTokens(gameId, team.getId()));
            }

            if (allTokens.isEmpty()) {
                logger.info("No push tokens registered for game {}", gameId);
                return;
            }
            // Provide a minimal data payload so background / tap handlers have context
            Map<String, Object> baseData = new HashMap<>();
            baseData.put("event", "game_event");
            baseData.put("gameId", gameId);
            baseData.put("title", title);
            baseData.put("body", body);
            baseData.put("timestamp", System.currentTimeMillis());

            sendToTokens(allTokens, title, body, baseData);
        } catch (Exception e) {
            logger.error("Failed to send game event notification for game {}: {}", gameId, e.getMessage());
        }
    }

    public void sendTeamFoundNotification(String gameId, String foundTeamName) {
        String title = "Team Found!";
        String body = foundTeamName + " has been found!";
        sendGameEventNotification(gameId, title, body);
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
            if (tokens == null || tokens.isEmpty()) {
                logger.warn("No push tokens found for hider team {} in game {}", targetHiderTeamId, gameId);
                return;
            }
            String title = "Clue requested on you";
            String body = "Heads up: '" + clueTypeName + "' was requested on your team.";
            Map<String, Object> data = Map.of(
                    "event", "clue_requested",
                    "gameId", gameId,
                    "targetTeamId", targetHiderTeamId,
                    "clueTypeId", clueTypeId,
                    "clueTypeName", clueTypeName
            );
            logger.info("Sending clue request notification to {} devices for hider team {} in game {}", 
                    tokens.size(), targetHiderTeamId, gameId);
            sendToTokens(tokens, title, body, data);
        } catch (Exception e) {
            logger.warn("Failed to send clue request notification: {}", e.toString());
        }
    }

    public void notifyClueTimeoutReward(String gameId, String requestingTeamId, String targetHiderTeamId, String clueTypeName, String hiderTeamName) {
        try {
            // Notify the requesting seeker team about their reward
            Set<String> seekerTokens = gameStore.getPushTokens(gameId, requestingTeamId);
            if (seekerTokens != null && !seekerTokens.isEmpty()) {
                String seekerTitle = "Timeout Reward!";
                String seekerBody = "You received exact location of " + hiderTeamName + " because they didn't respond to your " + clueTypeName + " request in time.";
                Map<String, Object> seekerData = Map.of(
                        "event", "clue_timeout_reward",
                        "gameId", gameId,
                        "requestingTeamId", requestingTeamId,
                        "targetHiderTeamId", targetHiderTeamId,
                        "clueTypeName", clueTypeName,
                        "hiderTeamName", hiderTeamName,
                        "recipient", "seeker"
                );
                logger.info("Sending timeout reward notification to {} seeker devices for team {} in game {}", 
                        seekerTokens.size(), requestingTeamId, gameId);
                sendToTokens(seekerTokens, seekerTitle, seekerBody, seekerData);
            }

            // Notify the target hider team about their penalty
            Set<String> hiderTokens = gameStore.getPushTokens(gameId, targetHiderTeamId);
            if (hiderTokens != null && !hiderTokens.isEmpty()) {
                String hiderTitle = "Clue Timeout Penalty";
                String hiderBody = "Your exact location was revealed to seekers because you didn't respond to the " + clueTypeName + " request in time.";
                Map<String, Object> hiderData = Map.of(
                        "event", "clue_timeout_penalty",
                        "gameId", gameId,
                        "requestingTeamId", requestingTeamId,
                        "targetHiderTeamId", targetHiderTeamId,
                        "clueTypeName", clueTypeName,
                        "hiderTeamName", hiderTeamName,
                        "recipient", "hider"
                );
                logger.info("Sending timeout penalty notification to {} hider devices for team {} in game {}", 
                        hiderTokens.size(), targetHiderTeamId, gameId);
                sendToTokens(hiderTokens, hiderTitle, hiderBody, hiderData);
            }
        } catch (Exception e) {
            logger.warn("Failed to send clue timeout reward/penalty notifications: {}", e.toString());
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

    // Emergency notification with maximum priority for critical game events
    public void sendEmergencyNotification(String gameId, String title, String body, Map<String, Object> data) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) {
                logger.warn("Cannot send emergency notification: game {} not found", gameId);
                return;
            }

            // Get all push tokens for all teams in the game
            Set<String> allTokens = new HashSet<>();
            for (var team : game.getTeams()) {
                allTokens.addAll(gameStore.getPushTokens(gameId, team.getId()));
            }

            if (allTokens.isEmpty()) {
                logger.info("No push tokens registered for emergency notification in game {}", gameId);
                return;
            }

            sendEmergencyBatch(allTokens, title, body, data);
            logger.info("Sent emergency notification to {} tokens for game {}", allTokens.size(), gameId);
        } catch (Exception e) {
            logger.error("Failed to send emergency notification for game {}: {}", gameId, e.getMessage());
        }
    }

    private void sendEmergencyBatch(Set<String> tokens, String title, String body, Map<String, Object> data) {
        try {
            List<String> tokenList = new ArrayList<>(tokens);
            int batchSize = 100;
            for (int i = 0; i < tokenList.size(); i += batchSize) {
                List<String> batch = tokenList.subList(i, Math.min(i + batchSize, tokenList.size()));
                
                List<Map<String, Object>> messages = new ArrayList<>();
                for (String token : batch) {
                    if (token == null || token.isBlank()) continue;
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("to", token);
                    msg.put("title", title);
                    msg.put("body", body);
                    msg.put("sound", "default");
                    msg.put("priority", "high");  // High = maximum supported FCM/Expo delivery priority
                    msg.put("channelId", "emergency");
                    msg.put("ttl", 0);           // ⚡ Immediate delivery
                    
                    // Android emergency settings
                    Map<String, Object> android = new HashMap<>();
                    android.put("priority", "high");
                    android.put("channelId", "emergency");
                    android.put("sound", "default");
                    android.put("vibrate", Arrays.asList(1000, 1000, 1000));
                    android.put("lights", true);
                    android.put("color", "#FF0000");
                    android.put("sticky", true);
                    android.put("autoCancel", false);
                    android.put("ongoing", true);     // ⚡ Keep persistent
                    android.put("fullScreenIntent", true); // ⚡ Full screen takeover
                    msg.put("android", android);
                    
                    // iOS critical alert settings (updated with latest features)
                    Map<String, Object> ios = new HashMap<>();
                    ios.put("sound", "default");
                    ios.put("badge", 1);
                    ios.put("_displayInForeground", true);
                    ios.put("critical", true);  // Bypass silent mode
                    ios.put("interruptionLevel", "critical"); // Strongest interruption level - bypasses Focus completely
                    msg.put("ios", ios);
                    
                    // Always attach a data payload so background handlers can react after user taps
                    Map<String, Object> payloadData = new HashMap<>();
                    payloadData.put("event", data != null && data.get("event") != null ? data.get("event") : "emergency");
                    payloadData.put("title", title);
                    payloadData.put("body", body);
                    payloadData.put("timestamp", System.currentTimeMillis());
                    if (data != null) payloadData.putAll(data);
                    msg.put("data", payloadData);
                    messages.add(msg);
                }
                
                if (!messages.isEmpty()) {
                    String json = objectMapper.writeValueAsString(messages);
                    HttpRequest.Builder builder = HttpRequest.newBuilder()
                            .uri(URI.create(getExpoPushEndpoint()))
                            .timeout(Duration.ofSeconds(15)) // Longer timeout for emergency
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
                    getExpoAccessToken().ifPresent(tok -> builder.header("Authorization", "Bearer " + tok));

                    HttpRequest request = builder.build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.debug("Sent emergency push batch ({} tokens).", messages.size());
                    } else {
                        logger.warn("Emergency Expo push failed: status={} body={}", response.statusCode(), response.body());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error sending emergency Expo push batch: {}", e.toString());
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
                
                // Following ChatGPT/StackOverflow guidelines for high-priority notifications
                Map<String, Object> msg = new HashMap<>();
                msg.put("to", token);
                msg.put("title", title);
                msg.put("body", body);
                msg.put("sound", "default");        // iOS + Android sound
                msg.put("priority", "high");         // High is the supported expedited priority for FCM/Expo
                msg.put("channelId", "urgent");     // Use the "urgent" channel from guidelines
                msg.put("badge", 1);                // iOS badge number
                msg.put("ttl", 0);                  // ⚡ CRITICAL: Immediate delivery or drop (no queuing)
                
                // Enhanced iOS settings following latest Expo docs
                Map<String, Object> ios = new HashMap<>();
                ios.put("sound", "default");
                ios.put("badge", 1);
                ios.put("interruptionLevel", "critical"); // ⚡ UPGRADED: Use critical for ALL notifications
                ios.put("_displayInForeground", true);
                ios.put("critical", true); // ⚡ CRITICAL: Bypass silent mode for ALL notifications
                msg.put("ios", ios);
                
                // Enhanced Android settings
                Map<String, Object> android = new HashMap<>();
                android.put("channelId", "urgent");
                android.put("priority", "high"); // High = fastest supported delivery
                android.put("sound", "default");
                android.put("vibrationPattern", Arrays.asList(0, 250, 250, 250));
                android.put("color", "#003366");
                android.put("lights", true); // ⚡ Enable LED lights
                android.put("sticky", true); // ⚡ Prevent swipe-to-dismiss
                android.put("autoCancel", false); // ⚡ Don't auto-dismiss when tapped
                android.put("ongoing", true); // ⚡ Keep notification persistent
                msg.put("android", android);
                
                // Always include a data section for background / tap navigation logic
                Map<String, Object> payloadData = new HashMap<>();
                payloadData.put("event", data != null && data.get("event") != null ? data.get("event") : "generic");
                payloadData.put("gameId", data != null && data.get("gameId") != null ? data.get("gameId") : null);
                payloadData.put("title", title);
                payloadData.put("body", body);
                payloadData.put("timestamp", System.currentTimeMillis());
                if (data != null) payloadData.putAll(data);
                // Remove nulls (e.g., gameId when absent)
                payloadData.entrySet().removeIf(en -> en.getValue() == null);
                msg.put("data", payloadData);
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
