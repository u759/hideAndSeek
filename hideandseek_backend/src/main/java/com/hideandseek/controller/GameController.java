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

    @Autowired
    private GameService gameService;

    @PostMapping("/game")
    public ResponseEntity<Game> createGame(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> teamNames = (List<String>) request.get("teamNames");
            String playerRole = (String) request.get("playerRole");
            
            Game game;
            if (playerRole != null && teamNames != null && teamNames.size() == 1) {
                // Single player game with role
                game = gameService.createGameWithRole(teamNames, playerRole);
            } else if (teamNames != null && teamNames.size() >= 2) {
                // Multi-team game
                game = gameService.createGame(teamNames);
            } else {
                return ResponseEntity.badRequest().build();
            }
            
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
    public ResponseEntity<Game> startGame(@PathVariable String gameId) {
        try {
            Game game = gameService.startGame(gameId);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
    public ResponseEntity<Game> resumeGame(@PathVariable String gameId) {
        try {
            Game game = gameService.updateGameStatus(gameId, "active");
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/game/{gameId}/status")
    public ResponseEntity<Game> updateGameStatus(
            @PathVariable String gameId,
            @RequestBody Map<String, String> request) {
        try {
            String status = request.get("status");
            Game game = gameService.updateGameStatus(gameId, status);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/game/{gameId}/next-round")
    public ResponseEntity<Game> nextRound(@PathVariable String gameId) {
        try {
            Game game = gameService.nextRound(gameId);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
    public ResponseEntity<Team> markTeamFound(
            @PathVariable String gameId,
            @PathVariable String hiderId) {
        try {
            Team team = gameService.markTeamFound(gameId, hiderId);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
