package com.hideandseek.service;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.Clue;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClueService {

    private static final Logger logger = LoggerFactory.getLogger(ClueService.class);

    private final GameStore gameStore;
    private final GameWebSocketHandler webSocketHandler;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

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
}
