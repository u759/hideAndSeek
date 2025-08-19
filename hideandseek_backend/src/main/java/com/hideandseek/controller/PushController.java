package com.hideandseek.controller;

import com.hideandseek.service.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = "*")
public class PushController {

    @Autowired
    private PushService pushService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        try {
            String gameId = (String) body.get("gameId");
            String teamId = (String) body.get("teamId");
            String token = (String) body.get("token");
            pushService.registerToken(gameId, teamId, token);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestNotification(@RequestBody Map<String, Object> body) {
        try {
            String gameId = (String) body.get("gameId");
            String teamId = (String) body.get("teamId");
            pushService.sendTestNotification(gameId, teamId);
            return ResponseEntity.ok(Map.of("status", "sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
