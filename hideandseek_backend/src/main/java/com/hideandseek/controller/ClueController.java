package com.hideandseek.controller;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.ClueType;
import com.hideandseek.model.PurchasedClue;
import com.hideandseek.service.GameService;
import com.hideandseek.service.ClueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/types")
    public ResponseEntity<List<ClueType>> getClueTypes() {
        try {
            List<ClueType> clueTypes = clueService.getClueTypes();
            return ResponseEntity.ok(clueTypes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{gameId}/history")
    public ResponseEntity<List<PurchasedClue>> getClueHistory(@PathVariable String gameId) {
        try {
            List<PurchasedClue> history = clueService.getClueHistory(gameId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{gameId}/purchase-by-type")
    public ResponseEntity<?> purchaseClueByType(
            @PathVariable String gameId,
            @RequestParam String teamId,
            @RequestParam String clueTypeId) {
        try {
            PurchasedClue purchasedClue = clueService.purchaseClueByType(gameId, teamId, clueTypeId);
            return ResponseEntity.ok(purchasedClue);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to purchase clue"));
        }
    }

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
