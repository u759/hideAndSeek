package com.hideandseek.controller;

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
    private ClueService clueService;

    @GetMapping("/types")
    public ResponseEntity<?> getClueTypes() {
        try {
            var clueTypes = clueService.getClueTypes();
            return ResponseEntity.ok(clueTypes);
        } catch (Exception e) {
            log.error("Error fetching clue types", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch clue types"));
        }
    }

    @GetMapping("/{gameId}/teams/{teamId}/history")
    public ResponseEntity<?> getClueHistory(@PathVariable String gameId, @PathVariable String teamId) {
        try {
            var history = clueService.getClueHistory(gameId, teamId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error fetching clue history", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch clue history"));
        }
    }

    @PostMapping("/purchase")
    public ResponseEntity<?> purchaseClue(@RequestBody Map<String, Object> request) {
        try {
            String gameId = (String) request.get("gameId");
            String teamId = (String) request.get("purchasingTeamId");
            String clueTypeId = (String) request.get("clueTypeId");
            String description = (String) request.get("description");
            
            if (gameId == null || teamId == null || clueTypeId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields: gameId, purchasingTeamId, clueTypeId"));
            }

            var result = clueService.purchaseClue(gameId, teamId, clueTypeId, description);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error purchasing clue", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to purchase clue"));
        }
    }
}
