package com.hideandseek.controller;

import com.hideandseek.model.Challenge;
import com.hideandseek.model.Curse;
import com.hideandseek.service.ChallengeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/challenges")
@CrossOrigin(origins = "*")
public class ChallengeController {

    @Autowired
    private ChallengeService challengeService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllChallengesAndCurses() {
        try {
            Map<String, Object> data = challengeService.getAllChallengesAndCurses();
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/draw")
    public ResponseEntity<Map<String, Object>> drawCard(@RequestBody Map<String, Object> request) {
        try {
            String teamId = (String) request.get("teamId");
            String gameId = (String) request.get("gameId");
            
            Map<String, Object> result = challengeService.drawCard(gameId, teamId);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("veto")) {
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Cannot draw cards due to veto penalty",
                    "message", e.getMessage()
                ));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> completeChallenge(@RequestBody Map<String, Object> request) {
        try {
            String challengeTitle = (String) request.get("challengeTitle");
            String teamId = (String) request.get("teamId");
            String gameId = (String) request.get("gameId");
            
            Map<String, Object> result = challengeService.completeChallenge(gameId, teamId, challengeTitle);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/veto")
    public ResponseEntity<Map<String, Object>> vetoChallenge(@RequestBody Map<String, Object> request) {
        try {
            String challengeTitle = (String) request.get("challengeTitle");
            String teamId = (String) request.get("teamId");
            String gameId = (String) request.get("gameId");
            
            Map<String, Object> result = challengeService.vetoChallenge(gameId, teamId, challengeTitle);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
