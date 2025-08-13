package com.hideandseek.service;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import com.opencagedata.jopencage.JOpenCageGeocoder;
import com.opencagedata.jopencage.model.JOpenCageResponse;
import com.opencagedata.jopencage.model.JOpenCageResult;
import com.opencagedata.jopencage.model.JOpenCageReverseRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.github.cdimascio.dotenv.Dotenv;

@Service
public class ClueService {

    private static final Logger logger = LoggerFactory.getLogger(ClueService.class);

    private final GameStore gameStore;
    private final GameWebSocketHandler webSocketHandler;

    private final Random random = new Random();

    public ClueService(GameStore gameStore, GameWebSocketHandler webSocketHandler) {
        this.gameStore = gameStore;
        this.webSocketHandler = webSocketHandler;
    }

    public List<Map<String, Object>> getClueTypes() {
        return gameStore.getAllClueTypes().stream()
                .map(clueType -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", clueType.getId());
                    map.put("name", clueType.getName());
                    map.put("description", clueType.getDescription());
                    map.put("cost", clueType.getCost());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getClueHistory(String gameId, String teamId) {
        return gameStore.getClueHistoryForTeam(gameId, teamId).stream()
                .map(clue -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", clue.getId());
                    map.put("text", clue.getClueText());
                    map.put("cost", clue.getCost());
                    map.put("timestamp", clue.getTimestamp());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> purchaseClue(String gameId, String teamId, String clueTypeId, String description) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot purchase clues when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        // Check if team is a hider (hiders cannot purchase clues)
        if ("hider".equals(team.getRole())) {
            throw new IllegalStateException("Hiders cannot purchase clues. Only seekers can buy clues to find hiders.");
        }

        var clueType = gameStore.getAllClueTypes().stream()
                .filter(ct -> ct.getId().equals(clueTypeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Clue type not found"));

        if (team.getTokens() < clueType.getCost()) {
            throw new IllegalStateException("Insufficient tokens");
        }

        // Generate clue based on type
        String clueText = generateClueByType(game, team, clueTypeId, description);

        var purchasedClue = new com.hideandseek.model.PurchasedClue(
                UUID.randomUUID().toString(),
                clueTypeId,
                teamId,
                gameId,
                clueText,
                clueType.getCost()
        );

        if (!game.getTeams().stream()
                .filter(t -> "hider".equals(t.getRole()) && t.getLocation() != null)
                .toList().isEmpty()) {
            // Deduct tokens
            team.setTokens(team.getTokens() - clueType.getCost());
            gameStore.updateGame(game);

            // Add to clue history
            gameStore.addClueToHistory(gameId, purchasedClue);
        }

        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        return result;
    }

    private String generateClueByType(Game game, Team requestingTeam, String clueTypeId, String description) {
        List<Team> hiders = game.getTeams().stream()
                .filter(t -> "hider".equals(t.getRole()) && t.getLocation() != null)
                .collect(Collectors.toList());

        if (hiders.isEmpty()) {
            return "No hiders with locations found.";
        }

        // Use AI for certain clue types, predefined logic for others
        switch (clueTypeId) {
            case "campus-landmark":
            case "building-hint":
            case "academic-area":
                return generateAIClue(hiders, clueTypeId, description);

            case "directional-compass":
                return generateDirectionalClue(hiders, requestingTeam);

            case "distance-range":
                return generateDistanceClue(hiders, requestingTeam);

            case "hot-cold":
                return generateHotColdClue(hiders, requestingTeam);

            case "elevation-hint":
                return generateElevationClue(hiders);

            case "crowd-level":
                return generateCrowdLevelClue(hiders);

            case "outdoor-indoor":
                return generateIndoorOutdoorClue(hiders);

            case "precise-location":
                return generatePreciseLocationClue(hiders);

            default:
                return "Unknown clue type requested.";
        }
    }

    // AI-based clue generator (uses Gemini API if available)
    private String generateAIClue(List<Team> hiders, String clueTypeId, String description) {

        Team closestHider = hiders.stream()
                .min((h1, h2) -> {
                    double dist1 = calculateDistance(h1.getLocation(), hiders.get(0).getLocation());
                    double dist2 = calculateDistance(h2.getLocation(), hiders.get(0).getLocation());
                    return Double.compare(dist1, dist2);
                })
                .orElse(null);

        if (closestHider == null) {
            return "No hiders found.";
        }

        Dotenv dotenv = Dotenv.load();

        JOpenCageGeocoder geocoder = new JOpenCageGeocoder(dotenv.get("OPENCAGE_API_KEY"));

        JOpenCageReverseRequest request =
                new JOpenCageReverseRequest(closestHider.getLocation().getLatitude(), closestHider.getLocation().getLongitude());

        request.setLanguage("en");
        request.setNoAnnotations(true);

        StringBuilder result = new StringBuilder();

        JOpenCageResponse geocoded = geocoder.reverse(request);

        for (JOpenCageResult resultItem : geocoded.getResults()) {
            if (resultItem.getFormatted() != null) {
                result.append(resultItem.getFormatted()).append("\n");
            }
        }

        Client client = Client.builder().apiKey(dotenv.get("GEMINI_API_KEY")).build();

        String prompt = String.format(
                "Generate a %s clue for the seekers with hiders located in the general vicinity of the" +
                        " following reverse-geocoded location: %s. The clue should follow" +
                        " the instructions in this description: %s. Only return the clue text, no additional information." +
                        " It should be 1-2 sentences long. Only return plaintext, no markdown or other formatting.",
                clueTypeId, result, description);

        System.out.println(prompt);

        GenerateContentResponse response =
                client.models.generateContent(
                        "gemma-3-27b-it",
                        prompt,
                        null);
        System.out.println(response.text());
        return response.text();
    }

    // Predefined logic-based clue generators
    private String generateDirectionalClue(List<Team> hiders, Team requestingTeam) {
        if (requestingTeam.getLocation() == null) {
            return "Cannot determine direction without your current location.";
        }

        Team closestHider = hiders.stream()
                .min((h1, h2) -> {
                    double dist1 = calculateDistance(requestingTeam.getLocation(), h1.getLocation());
                    double dist2 = calculateDistance(requestingTeam.getLocation(), h2.getLocation());
                    return Double.compare(dist1, dist2);
                })
                .orElse(null);

        if (closestHider == null) return "No hiders found.";

        String direction = getDirection(requestingTeam.getLocation(), closestHider.getLocation());
        return String.format("The closest hider is generally to the %s of your current position.", direction);
    }

    private String generateDistanceClue(List<Team> hiders, Team requestingTeam) {
        if (requestingTeam.getLocation() == null) {
            return "Cannot determine distance without your current location.";
        }

        double avgDistance = hiders.stream()
                .mapToDouble(h -> calculateDistance(requestingTeam.getLocation(), h.getLocation()))
                .average()
                .orElse(0);

        if (avgDistance < 100) return "The hiders are very close - within 100 meters!";
        else if (avgDistance < 300) return "The hiders are nearby - within 300 meters.";
        else if (avgDistance < 500) return "The hiders are moderately far - within 500 meters.";
        else return "The hiders are quite far - over 500 meters away.";
    }

    private String generateHotColdClue(List<Team> hiders, Team requestingTeam) {
        if (requestingTeam.getLocation() == null) {
            return "Cannot determine temperature without your current location.";
        }

        double minDistance = hiders.stream()
                .mapToDouble(h -> calculateDistance(requestingTeam.getLocation(), h.getLocation()))
                .min()
                .orElse(Double.MAX_VALUE);

        if (minDistance < 50) return "🔥 You're on fire! Very hot!";
        else if (minDistance < 150) return "🌡️ Getting warmer...";
        else if (minDistance < 300) return "😐 Lukewarm.";
        else if (minDistance < 500) return "🧊 Getting cold...";
        else return "❄️ Freezing! You're way off.";
    }

    private String generateElevationClue(List<Team> hiders) {
        String[] elevationHints = {
                "The hiders are on ground level, blending with the campus crowd.",
                "Look up! The hiders have taken to higher ground.",
                "The hiders have gone underground or to lower levels."
        };
        return elevationHints[random.nextInt(elevationHints.length)];
    }

    private String generateCrowdLevelClue(List<Team> hiders) {
        String[] crowdHints = {
                "The hiders are in a bustling, high-traffic area of campus.",
                "The hiders have found a moderately busy spot with some foot traffic.",
                "The hiders are hiding in a quiet, secluded area of campus."
        };
        return crowdHints[random.nextInt(crowdHints.length)];
    }

    private String generateIndoorOutdoorClue(List<Team> hiders) {
        return random.nextBoolean() ?
                "The hiders are inside a building, sheltered from the elements." :
                "The hiders are outside, enjoying the fresh campus air.";
    }

    private String generatePreciseLocationClue(List<Team> hiders) {
        Team randomHider = hiders.get(random.nextInt(hiders.size()));
        return String.format("Exact location of team %s: %.6f, %.6f",
                randomHider.getName(),
                randomHider.getLocation().getLatitude(),
                randomHider.getLocation().getLongitude());
    }

    private String generateFallbackClue(List<Team> hiders, String clueTypeId) {
        String[] fallbackClues = {
                "The shadows dance near the heart of learning, where books whisper ancient secrets.",
                "Between towers of knowledge and pathways of discovery, the hidden ones wait.",
                "Where students gather to fuel their minds and bodies, secrets may be concealed.",
                "In the realm where science meets nature, look for those who lurk in plain sight."
        };
        return fallbackClues[random.nextInt(fallbackClues.length)];
    }

    // Utility methods
    private double calculateDistance(Team.TeamLocation loc1, Team.TeamLocation loc2) {
        double lat1 = Math.toRadians(loc1.getLatitude());
        double lon1 = Math.toRadians(loc1.getLongitude());
        double lat2 = Math.toRadians(loc2.getLatitude());
        double lon2 = Math.toRadians(loc2.getLongitude());

        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;

        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double radius = 6371000; // Earth's radius in meters

        return radius * c;
    }

    private String getDirection(Team.TeamLocation from, Team.TeamLocation to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lon1 = Math.toRadians(from.getLongitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double lon2 = Math.toRadians(to.getLongitude());

        double dlon = lon2 - lon1;
        double y = Math.sin(dlon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dlon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        bearing = (bearing + 360) % 360;

        if (bearing < 22.5 || bearing >= 337.5) return "north";
        else if (bearing < 67.5) return "northeast";
        else if (bearing < 112.5) return "east";
        else if (bearing < 157.5) return "southeast";
        else if (bearing < 202.5) return "south";
        else if (bearing < 247.5) return "southwest";
        else if (bearing < 292.5) return "west";
        else return "northwest";
    }
}
