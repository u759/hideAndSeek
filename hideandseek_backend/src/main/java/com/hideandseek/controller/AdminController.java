package com.hideandseek.controller;

import com.hideandseek.store.GameStore;
import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameService gameService;

    /**
     * Get comprehensive system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        try {
            List<Game> allGames = gameStore.getAllGames();
            
            Map<String, Object> stats = new HashMap<>();
            
            // Basic counts
            stats.put("totalGames", allGames.size());
            stats.put("activeGames", allGames.stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()))
                .count());
            stats.put("pausedGames", allGames.stream()
                .filter(g -> "PAUSED".equals(g.getStatus()))
                .count());
            stats.put("endedGames", allGames.stream()
                .filter(g -> "ENDED".equals(g.getStatus()))
                .count());
            
            // Player statistics
            int totalPlayers = allGames.stream()
                .mapToInt(g -> g.getTeams().size())
                .sum();
            stats.put("totalPlayers", totalPlayers);
            
            int activePlayers = allGames.stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()) || "PAUSED".equals(g.getStatus()))
                .mapToInt(g -> g.getTeams().size())
                .sum();
            stats.put("activePlayers", activePlayers);
            
            // Game status breakdown
            Map<String, Long> statusBreakdown = allGames.stream()
                .collect(Collectors.groupingBy(
                    Game::getStatus,
                    Collectors.counting()
                ));
            stats.put("gamesByStatus", statusBreakdown);
            
            // Team role distribution (for active games)
            Map<String, Long> roleDistribution = allGames.stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()) || "PAUSED".equals(g.getStatus()))
                .flatMap(g -> g.getTeams().stream())
                .collect(Collectors.groupingBy(
                    Team::getRole,
                    Collectors.counting()
                ));
            stats.put("teamsByRole", roleDistribution);
            
            // Average game duration for ended games (in minutes)
            OptionalDouble avgDuration = allGames.stream()
                .filter(g -> "ENDED".equals(g.getStatus()) && g.getStartTime() != null && g.getEndTime() != null)
                .mapToLong(g -> g.getEndTime() - g.getStartTime())
                .average();
            
            if (avgDuration.isPresent()) {
                stats.put("averageGameDurationMinutes", Math.round(avgDuration.getAsDouble() / (1000 * 60)));
            } else {
                stats.put("averageGameDurationMinutes", 0);
            }
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get system stats: " + e.getMessage()));
        }
    }

    /**
     * Get detailed information about all games
     */
    @GetMapping("/games")
    public ResponseEntity<List<Map<String, Object>>> getAllGamesDetailed() {
        try {
            List<Game> allGames = gameStore.getAllGames();
            
            List<Map<String, Object>> gameDetails = allGames.stream()
                .map(this::formatGameForAdmin)
                .collect(Collectors.toList());
            
            // Sort by creation time (newest first)
            gameDetails.sort((a, b) -> {
                Long timeA = (Long) a.get("startTime");
                Long timeB = (Long) b.get("startTime");
                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return 1;
                if (timeB == null) return -1;
                return timeB.compareTo(timeA);
            });
            
            return ResponseEntity.ok(gameDetails);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of(Map.of("error", "Failed to get games: " + e.getMessage())));
        }
    }

    /**
     * Delete a specific game
     */
    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Map<String, Object>> deleteGame(@PathVariable String gameId) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }
            
            gameService.deleteGame(gameId);
            return ResponseEntity.ok(Map.of(
                "message", "Game deleted successfully",
                "gameId", gameId
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete game: " + e.getMessage()));
        }
    }

    /**
     * Delete all ended games
     */
    @DeleteMapping("/games/cleanup/ended")
    public ResponseEntity<Map<String, Object>> deleteAllEndedGames() {
        try {
            List<Game> allGames = gameStore.getAllGames();
            List<String> deletedGameIds = new ArrayList<>();
            
            for (Game game : allGames) {
                if ("ENDED".equals(game.getStatus())) {
                    gameService.deleteGame(game.getId());
                    deletedGameIds.add(game.getId());
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Ended games deleted successfully",
                "deletedCount", deletedGameIds.size(),
                "deletedGameIds", deletedGameIds
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to cleanup ended games: " + e.getMessage()));
        }
    }

    /**
     * Delete all games (nuclear option)
     */
    @DeleteMapping("/games/cleanup/all")
    public ResponseEntity<Map<String, Object>> deleteAllGames() {
        try {
            List<Game> allGames = gameStore.getAllGames();
            List<String> deletedGameIds = new ArrayList<>();
            
            for (Game game : allGames) {
                gameService.deleteGame(game.getId());
                deletedGameIds.add(game.getId());
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "All games deleted successfully",
                "deletedCount", deletedGameIds.size(),
                "deletedGameIds", deletedGameIds
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete all games: " + e.getMessage()));
        }
    }

    /**
     * Get detailed information about a specific game
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<Map<String, Object>> getGameDetails(@PathVariable String gameId) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> details = formatGameForAdmin(game);
            
            // Add extra admin-specific details
            details.put("teamDetails", game.getTeams().stream()
                .map(this::formatTeamForAdmin)
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(details);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get game details: " + e.getMessage()));
        }
    }

    /**
     * Lightweight game snapshot for live map view with coordinates.
     */
    @GetMapping("/games/{gameId}/live")
    public ResponseEntity<Map<String, Object>> getLiveGame(@PathVariable String gameId) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", game.getId());
            payload.put("gameCode", game.getCode());
            payload.put("status", game.getStatus());
            payload.put("round", game.getRound());
            payload.put("roundLengthMinutes", game.getRoundLengthMinutes());
            payload.put("startTime", game.getStartTime());
            payload.put("endTime", game.getEndTime());
            payload.put("teams", game.getTeams().stream().map(team -> {
                Map<String, Object> t = new HashMap<>();
                t.put("id", team.getId());
                t.put("name", team.getName());
                t.put("role", team.getRole());
                t.put("tokens", team.getTokens());
                if (team.getLocation() != null) {
                    t.put("latitude", team.getLocation().getLatitude());
                    t.put("longitude", team.getLocation().getLongitude());
                    t.put("timestamp", team.getLocation().getTimestamp());
                }
                return t;
            }).collect(Collectors.toList()));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get live game data: " + e.getMessage()));
        }
    }

    /**
     * Force end a game
     */
    @PostMapping("/games/{gameId}/force-end")
    public ResponseEntity<Map<String, Object>> forceEndGame(@PathVariable String gameId) {
        try {
            Game game = gameStore.getGame(gameId);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }
            
            gameService.endGame(gameId);
            return ResponseEntity.ok(Map.of(
                "message", "Game force-ended successfully",
                "gameId", gameId
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to force-end game: " + e.getMessage()));
        }
    }

    private Map<String, Object> formatGameForAdmin(Game game) {
        Map<String, Object> details = new HashMap<>();
        details.put("id", game.getId());
        details.put("gameCode", game.getCode());
        details.put("status", game.getStatus());
        details.put("currentRound", game.getRound());
        details.put("roundLengthMinutes", game.getRoundLengthMinutes());
        details.put("startTime", game.getStartTime());
        details.put("endTime", game.getEndTime());
        details.put("teamCount", game.getTeams().size());
        
        // Team summary
        Map<String, Long> teamsByRole = game.getTeams().stream()
            .collect(Collectors.groupingBy(Team::getRole, Collectors.counting()));
        details.put("teamsByRole", teamsByRole);
        
        // Game duration if ended
        if (game.getStartTime() != null && game.getEndTime() != null) {
            long durationMs = game.getEndTime() - game.getStartTime();
            details.put("durationMinutes", Math.round(durationMs / (1000.0 * 60)));
        }
        
        // Current round duration if active
        if (game.getStartTime() != null && "ACTIVE".equals(game.getStatus())) {
            long currentRoundMs = System.currentTimeMillis() - game.getStartTime();
            details.put("currentRoundMinutes", Math.round(currentRoundMs / (1000.0 * 60)));
        }
        
        return details;
    }
    
    private Map<String, Object> formatTeamForAdmin(Team team) {
        Map<String, Object> details = new HashMap<>();
        details.put("id", team.getId());
        details.put("name", team.getName());
        details.put("role", team.getRole());
        details.put("tokens", team.getTokens());
        details.put("activeChallenge", team.getActiveChallenge() != null ? team.getActiveChallenge().getChallenge().getTitle() : null);
        details.put("completedChallenges", team.getCompletedChallenges().size());
        details.put("activeCurses", team.getActiveCurses().size());
        details.put("vetoEndTime", team.getVetoEndTime());
        
        // Location information
        if (team.getLocation() != null) {
            details.put("lastLocationUpdate", team.getLocation().getTimestamp());
            details.put("hasLocation", true);
            details.put("latitude", team.getLocation().getLatitude());
            details.put("longitude", team.getLocation().getLongitude());
        } else {
            details.put("hasLocation", false);
        }
        
        return details;
    }
}
