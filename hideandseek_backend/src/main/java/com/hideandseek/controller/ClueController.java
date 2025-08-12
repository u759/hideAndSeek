package com.hideandseek.controller;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.service.GameService;
import com.hideandseek.service.ClueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clues")
@CrossOrigin(origins = "*")
public class ClueController {

    private static final Logger log = LoggerFactory.getLogger(ClueController.class);

    @Autowired
    private GameService gameService;
    
    @Autowired
    private ClueService clueService;

    @PostMapping("/purchase")
    public ResponseEntity<?> purchaseClue(@RequestBody Map<String, Object> request) {
        try {
            String gameId = (String) request.get("gameId");
            String teamId = (String) request.get("teamId");
            Integer tokenCost = (Integer) request.get("cost");
            
            if (gameId == null || teamId == null || tokenCost == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields: gameId, teamId, cost"));
            }

            Game game = gameService.getGame(gameId);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }

            Team team = game.getTeams().stream()
                    .filter(t -> t.getId().equals(teamId))
                    .findFirst()
                    .orElse(null);
                    
            if (team == null) {
                return ResponseEntity.notFound().build();
            }

            if (team.getTokens() < tokenCost) {
                return ResponseEntity.badRequest().body(Map.of("error", "Insufficient tokens"));
            }

            // Generate clue based on hider locations
            String clue = clueService.generateClue(game, team);
            
            // Deduct tokens
            team.setTokens(team.getTokens() - tokenCost);
            
            log.info("Team {} purchased clue for {} tokens in game {}", teamId, tokenCost, gameId);
            
            return ResponseEntity.ok(Map.of("clue", clue));
            
        } catch (Exception e) {
            log.error("Error purchasing clue", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to purchase clue"));
        }
    }
}
