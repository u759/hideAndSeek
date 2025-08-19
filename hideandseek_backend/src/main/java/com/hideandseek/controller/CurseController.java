package com.hideandseek.controller;

import com.hideandseek.model.Team;
import com.hideandseek.service.CurseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/curse")
public class CurseController {

    @Autowired
    private CurseService curseService;

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyCurse(@RequestBody Map<String, Object> request) {
        try {
            String gameId = (String) request.get("gameId");
            String seekerTeamId = (String) request.get("seekerTeamId");
            String targetTeamId = (String) request.get("targetTeamId");
            
            Map<String, Object> result = curseService.curseTeam(gameId, seekerTeamId, targetTeamId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/targets/{gameId}/{seekerTeamId}")
    public ResponseEntity<List<Team>> getAvailableTargets(
            @PathVariable String gameId,
            @PathVariable String seekerTeamId) {
        try {
            List<Team> targets = curseService.getAvailableTargets(gameId, seekerTeamId);
            return ResponseEntity.ok(targets);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/complete")
    public ResponseEntity<?> markCurseCompleted(@RequestBody Map<String, Object> body) {
        try {
            String gameId = (String) body.get("gameId");
            String teamId = (String) body.get("teamId");
            String curseId = (String) body.get("curseId");
            return ResponseEntity.ok(curseService.markCurseCompleted(gameId, teamId, curseId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
