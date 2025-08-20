package com.hideandseek.controller;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.Location;
import com.hideandseek.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GameController {
    @GetMapping("/game/{gameId}/stats")
    public ResponseEntity<?> getGameStats(@PathVariable String gameId) {
        try {
            Map<String, Object> stats = gameService.getGameStats(gameId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get game stats"));
        }
    }

    @Autowired
    private GameService gameService;

    @PostMapping("/game")
    public ResponseEntity<?> createGame(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> teamNames = (List<String>) request.get("teamNames");
            String playerRole = (String) request.get("playerRole");
            Integer roundLengthMinutes = (Integer) request.get("roundLengthMinutes");
            
            // Validate round length if provided
            if (roundLengthMinutes != null) {
                if (roundLengthMinutes <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Round duration must be a positive number"));
                }
                if (roundLengthMinutes > 999) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Round duration cannot exceed 999 minutes"));
                }
            }
            
            Game game;
            if (playerRole != null && teamNames != null && teamNames.size() == 1) {
                // Single player game with role
                game = gameService.createGameWithRole(teamNames, playerRole);
            } else if (teamNames != null && teamNames.size() >= 2) {
                // Multi-team game
                game = gameService.createGame(teamNames);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid team configuration"));
            }
            
            // Set round length if provided
            if (roundLengthMinutes != null && roundLengthMinutes > 0) {
                game.setRoundLengthMinutes(roundLengthMinutes);
            }
            
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create game: " + e.getMessage()));
        }
    }

    @GetMapping("/game/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable String gameId) {
        try {
            Game game = gameService.getGame(gameId);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/game/code/{gameCode}")
    public ResponseEntity<Game> getGameByCode(@PathVariable String gameCode) {
        try {
            Game game = gameService.getGameByCode(gameCode.toUpperCase());
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/games")
    public ResponseEntity<List<Game>> getAllGames() {
        List<Game> games = gameService.getAllGames();
        return ResponseEntity.ok(games);
    }

    @PostMapping("/game/{gameId}/start")
    public ResponseEntity<?> startGame(@PathVariable String gameId) {
        try {
            Game game = gameService.startGame(gameId);
            return ResponseEntity.ok(game);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to start game"));
        }
    }

    @PostMapping("/game/{gameId}/end")
    public ResponseEntity<Game> endGame(@PathVariable String gameId) {
        try {
            Game game = gameService.endGame(gameId);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/pause")
    public ResponseEntity<Game> pauseGame(@PathVariable String gameId) {
        try {
            Game game = gameService.updateGameStatus(gameId, "paused");
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/resume")
    public ResponseEntity<?> resumeGame(@PathVariable String gameId) {
        try {
            Game game = gameService.updateGameStatus(gameId, "active");
            return ResponseEntity.ok(game);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to resume game"));
        }
    }

    @PatchMapping("/game/{gameId}/status")
    public ResponseEntity<?> updateGameStatus(
            @PathVariable String gameId,
            @RequestBody Map<String, String> request) {
        try {
            String status = request.get("status");
            Game game = gameService.updateGameStatus(gameId, status);
            return ResponseEntity.ok(game);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update game status"));
        }
    }

    @PostMapping("/game/{gameId}/next-round")
    public ResponseEntity<?> nextRound(@PathVariable String gameId) {
        try {
            Game game = gameService.nextRound(gameId);
            return ResponseEntity.ok(game);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to start next round"));
        }
    }

    @PostMapping("/game/{gameId}/restart")
    public ResponseEntity<?> restartGame(@PathVariable String gameId) {
        try {
            Game game = gameService.restartGame(gameId);
            return ResponseEntity.ok(game);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to restart game"));
        }
    }

    @PatchMapping("/game/{gameId}/teams/{teamId}/tokens")
    public ResponseEntity<Team> updateTeamTokens(
            @PathVariable String gameId,
            @PathVariable String teamId,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer tokens = request.get("tokens");
            Team team = gameService.updateTeamTokens(gameId, teamId, tokens);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/teams/{teamId}/role")
    public ResponseEntity<Team> switchTeamRole(
            @PathVariable String gameId,
            @PathVariable String teamId,
            @RequestBody Map<String, String> request) {
        try {
            String role = request.get("role");
            String foundByTeamId = request.get("foundByTeamId");
            Team team = gameService.switchTeamRole(gameId, teamId, role, foundByTeamId);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/game/{gameId}/teams/{teamId}/role")
    public ResponseEntity<?> updateTeamRole(
            @PathVariable String gameId,
            @PathVariable String teamId,
            @RequestBody Map<String, String> request) {
        try {
            String newRole = request.get("role");
            if (!"seeker".equals(newRole) && !"hider".equals(newRole)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role. Must be 'seeker' or 'hider'"));
            }
            
            Game game = gameService.getGame(gameId);
            if (!"paused".equals(game.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Can only change roles when game is paused"));
            }
            
            Team team = gameService.updateTeamRole(gameId, teamId, newRole);
            return ResponseEntity.ok(team);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update team role"));
        }
    }

    @DeleteMapping("/game/{gameId}")
    public ResponseEntity<Void> deleteGame(@PathVariable String gameId) {
        try {
            gameService.deleteGame(gameId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/teams/{teamId}/location")
    public ResponseEntity<Team> updateTeamLocation(
            @PathVariable String gameId,
            @PathVariable String teamId,
            @RequestBody Location location) {
        try {
            Team team = gameService.updateTeamLocation(gameId, teamId, location);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/teams/{teamId}/challenge/draw")
    public ResponseEntity<Team> drawChallenge(
            @PathVariable String gameId,
            @PathVariable String teamId) {
        try {
            Team team = gameService.drawChallenge(gameId, teamId);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/teams/{teamId}/challenge/complete")
    public ResponseEntity<Team> completeChallenge(
            @PathVariable String gameId,
            @PathVariable String teamId) {
        try {
            Team team = gameService.completeChallenge(gameId, teamId);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/teams/{teamId}/challenge/refuse")
    public ResponseEntity<Team> refuseChallenge(
            @PathVariable String gameId,
            @PathVariable String teamId) {
        try {
            Team team = gameService.refuseChallenge(gameId, teamId);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/teams/{hiderId}/found")
    public ResponseEntity<?> markTeamFound(
            @PathVariable String gameId,
            @PathVariable String hiderId) {
        try {
            Map<String, Object> result = gameService.markTeamFoundWithGameInfo(gameId, hiderId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to mark hider as found"));
        }
    }
}
