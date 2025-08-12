package com.hideandseek.service;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.Clue;
import com.hideandseek.model.ClueType;
import com.hideandseek.model.PurchasedClue;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClueService {

    private static final Logger logger = LoggerFactory.getLogger(ClueService.class);

    private final GameStore gameStore;
    private final GameWebSocketHandler webSocketHandler;

    @Value("${openai.api.key:}")
    private String openaiApiKey;
    @Value("${opencagedata.api.key:}")
    private String opencagedataApiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

    public ClueService(GameStore gameStore, GameWebSocketHandler webSocketHandler) {
        this.gameStore = gameStore;
        this.webSocketHandler = webSocketHandler;
    }

    public Clue purchaseClue(String gameId, String teamId, int cost) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }
        
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot purchase clues. Game status: " + game.getStatus());
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        if (team.getTokens() < cost) {
            throw new IllegalStateException("Insufficient tokens");
        }

        // Get all hider locations
        List<Team> hiders = game.getTeams().stream()
                .filter(t -> "hider".equals(t.getRole()) && t.getLocation() != null)
                .collect(Collectors.toList());

        if (hiders.isEmpty()) {
            throw new IllegalStateException("No hiders with locations found");
        }

        // Generate clue using OpenAI
        String clueText = generateClueWithOpenAI(hiders);

        // Deduct tokens
        team.setTokens(team.getTokens() - cost);
        gameStore.updateGame(game);

        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Clue clue = new Clue();
        clue.setId(UUID.randomUUID().toString());
        clue.setText(clueText);
        clue.setCost(cost);

        return clue;
    }

    @SuppressWarnings("unchecked")
    private String generateClueWithOpenAI(List<Team> hiders) {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            // Fallback when no OpenAI key
            return generateFallbackClue(hiders);
        }

        try {
            // Build locations context
            StringBuilder locationsContext = new StringBuilder();
            for (Team hider : hiders) {
                Team.TeamLocation loc = hider.getLocation();
                locationsContext.append(String.format("Team %s: lat %.6f, lng %.6f\n",
                    hider.getName(), loc.getLatitude(), loc.getLongitude()));
            }

            String prompt = String.format("""
                You are generating location clues for a hide and seek game on the UBC Vancouver campus.
                Given the following hider team locations (latitude/longitude), generate a cryptic but helpful clue
                that would help seekers find them. Reference UBC landmarks, buildings, or areas near these coordinates.
                
                Hider locations:
                %s
                
                Generate a single clue (2-3 sentences max) that hints at where the hiders might be without being too specific.
                Make it fun and campus-themed.
                """, locationsContext);

            Map<String, Object> requestBody = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 100,
                "temperature", 0.7
            );

            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }

            return generateFallbackClue(hiders);

        } catch (Exception e) {
            logger.error("Failed to generate clue with OpenAI", e);
            return generateFallbackClue(hiders);
        }
    }

    public String generateClue(Game game, Team requestingTeam) {
        // Get all hider teams with their locations
        List<Team> hiderTeams = game.getTeams().stream()
                .filter(team -> "hider".equals(team.getRole()) && !team.getId().equals(requestingTeam.getId()))
                .filter(team -> team.getLocation() != null)
                .collect(Collectors.toList());

        if (hiderTeams.isEmpty()) {
            return "No hider teams found with valid locations.";
        }

        // Use existing OpenAI clue generation
        return generateClueWithOpenAI(hiderTeams);
    }

    private String generateFallbackClue(List<Team> hiders) {
        // Simple fallback clues based on general UBC areas
        String[] fallbackClues = {
            "The shadows dance near the heart of learning, where books whisper ancient secrets.",
            "Between towers of knowledge and pathways of discovery, the hidden ones wait.",
            "Where students gather to fuel their minds and bodies, secrets may be concealed.",
            "In the realm where science meets nature, look for those who lurk in plain sight."
        };
        
        return fallbackClues[hiders.size() % fallbackClues.length];
    }

    public List<ClueType> getClueTypes() {
        return gameStore.getAllClueTypes();
    }

    public List<PurchasedClue> getClueHistory(String gameId) {
        return gameStore.getClueHistory(gameId);
    }

    public PurchasedClue purchaseClueByType(String gameId, String teamId, String clueTypeId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }
        
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot purchase clues. Game status: " + game.getStatus());
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        ClueType clueType = gameStore.getClueTypeById(clueTypeId);
        if (clueType == null) {
            throw new IllegalArgumentException("Clue type not found");
        }

        if (team.getTokens() < clueType.getCost()) {
            throw new IllegalStateException("Insufficient tokens");
        }

        // Generate clue based on type
        String clueText = generateClueByType(game, team, clueType);

        // Deduct tokens
        team.setTokens(team.getTokens() - clueType.getCost());
        gameStore.updateGame(game);

        // Create and store purchased clue
        PurchasedClue purchasedClue = new PurchasedClue();
        purchasedClue.setId(UUID.randomUUID().toString());
        purchasedClue.setClueTypeId(clueTypeId);
        purchasedClue.setTeamId(teamId);
        purchasedClue.setGameId(gameId);
        purchasedClue.setClueText(clueText);
        purchasedClue.setCost(clueType.getCost());
        
        gameStore.addPurchasedClue(purchasedClue);

        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        return purchasedClue;
    }

    private String generateClueByType(Game game, Team requestingTeam, ClueType clueType) {
        List<Team> hiders = game.getTeams().stream()
                .filter(t -> "hider".equals(t.getRole()) && !t.getId().equals(requestingTeam.getId()))
                .filter(t -> t.getLocation() != null)
                .collect(Collectors.toList());

        if (hiders.isEmpty()) {
            return "No hiders with locations found.";
        }

        switch (clueType.getId()) {
            case "campus-landmark":
                return generateLandmarkClue(hiders);
            case "building-hint":
                return generateBuildingClue(hiders);
            case "directional-compass":
                return generateDirectionalClue(hiders, requestingTeam);
            case "distance-range":
                return generateDistanceClue(hiders, requestingTeam);
            case "academic-area":
                return generateAcademicAreaClue(hiders);
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
                return generateFallbackClue(hiders);
        }
    }

    private String generateLandmarkClue(List<Team> hiders) {
        String[] landmarks = {"Main Library", "Student Union Building", "Buchanan Tower", "Irving K. Barber Learning Centre", 
                             "Rose Garden", "Nitobe Memorial Garden", "Museum of Anthropology", "Chan Centre", "Thunderbird Arena"};
        return "The hiders are near " + landmarks[hiders.size() % landmarks.length] + " or a similar landmark.";
    }

    private String generateBuildingClue(List<Team> hiders) {
        String[] buildingTypes = {"academic building", "residence hall", "recreational facility", "library", 
                                 "administrative building", "research facility", "student services building"};
        return "Look for them around a " + buildingTypes[hiders.size() % buildingTypes.length] + ".";
    }

    private String generateDirectionalClue(List<Team> hiders, Team seeker) {
        // Simple directional hint (would need actual calculation in real implementation)
        String[] directions = {"north", "south", "east", "west", "northeast", "northwest", "southeast", "southwest"};
        return "Head " + directions[hiders.size() % directions.length] + " from your current position.";
    }

    private String generateDistanceClue(List<Team> hiders, Team seeker) {
        String[] distances = {"very close (under 100m)", "close (100-300m)", "moderate distance (300-600m)", 
                             "far (600m-1km)", "very far (over 1km)"};
        return "The hiders are " + distances[hiders.size() % distances.length] + " away.";
    }

    private String generateAcademicAreaClue(List<Team> hiders) {
        String[] areas = {"Science precinct", "Arts area", "Engineering zone", "Forest Sciences area", 
                         "Medicine/Health area", "Business school vicinity", "Applied Science region"};
        return "Search in the " + areas[hiders.size() % areas.length] + ".";
    }

    private String generateHotColdClue(List<Team> hiders, Team seeker) {
        String[] hotCold = {"getting warmer", "getting colder", "you're hot!", "you're cold", "lukewarm"};
        return "You're " + hotCold[hiders.size() % hotCold.length] + "!";
    }

    private String generateElevationClue(List<Team> hiders) {
        String[] elevations = {"at ground level", "above ground level", "below ground level", "multiple floors up", "basement level"};
        return "The hiders are " + elevations[hiders.size() % elevations.length] + ".";
    }

    private String generateCrowdLevelClue(List<Team> hiders) {
        String[] crowdLevels = {"in a busy, crowded area", "in a moderately populated area", "in a quiet, secluded area", 
                               "where many students gather", "in a peaceful spot"};
        return "They're hiding " + crowdLevels[hiders.size() % crowdLevels.length] + ".";
    }

    private String generateIndoorOutdoorClue(List<Team> hiders) {
        String[] locations = {"inside a building", "outside in the open", "in a covered outdoor area", 
                             "in an indoor space", "in an outdoor courtyard"};
        return "The hiders are " + locations[hiders.size() % locations.length] + ".";
    }

    private String generatePreciseLocationClue(List<Team> hiders) {
        Team randomHider = hiders.get(new Random().nextInt(hiders.size()));
        Team.TeamLocation loc = randomHider.getLocation();
        return String.format("One hider team is at coordinates: %.6f, %.6f", loc.getLatitude(), loc.getLongitude());
    }
}
