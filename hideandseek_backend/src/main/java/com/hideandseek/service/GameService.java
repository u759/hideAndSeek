package com.hideandseek.service;

import com.hideandseek.model.*;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class GameService {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameWebSocketHandler webSocketHandler;

    public Game createGame(List<String> teamNames) {
        if (teamNames == null || teamNames.isEmpty()) {
            throw new IllegalArgumentException("Team names cannot be empty");
        }

        Game game = gameStore.createGame(teamNames);

        return game;
    }

    public Game createGameWithRole(List<String> teamNames, String playerRole) {
        if (teamNames == null || teamNames.isEmpty()) {
            throw new IllegalArgumentException("Team names cannot be empty");
        }
        
        Game game = gameStore.createGameWithRole(teamNames, playerRole);

        return game;
    }

    public Game getGame(String gameId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }
        
        // Clean expired curses whenever a game is fetched
        cleanExpiredCurses(game);
        gameStore.updateGame(game);
        
        return game;
    }

    private void validateGameIsActive(Game game) {
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Game operation not allowed. Game status: " + game.getStatus());
        }
    }

    private void validateGameIsActiveOrWaiting(Game game) {
        if (!"active".equals(game.getStatus()) && !"waiting".equals(game.getStatus())) {
            throw new IllegalStateException("Game operation not allowed. Game status: " + game.getStatus());
        }
    }

    private void cleanExpiredCurses(Game game) {
        long currentTime = System.currentTimeMillis();
        
        for (Team team : game.getTeams()) {
            // Clean expired active curses
            team.getActiveCurses().removeIf(activeCurse -> activeCurse.getEndTime() <= currentTime);
            
            // Clean expired applied curses for seekers
            if ("seeker".equals(team.getRole())) {
                team.getAppliedCurses().removeIf(appliedCurse -> appliedCurse.getEndTime() <= currentTime);
            }
        }
    }

    public Game getGameByCode(String gameCode) {
        Game game = gameStore.getGameByCode(gameCode);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }
        return game;
    }

    public List<Game> getAllGames() {
        return gameStore.getAllGames();
    }

    public Game startGame(String gameId) {
        Game game = getGame(gameId);
        // Validate that there's at least 1 seeker and 1 hider before starting
        long seekers = game.getTeams().stream().filter(t -> "seeker".equals(t.getRole())).count();
        long hiders = game.getTeams().stream().filter(t -> "hider".equals(t.getRole())).count();
        if (seekers < 1) {
            throw new IllegalStateException("Need at least 1 seeker to start the game");
        }
        if (hiders < 1) {
            throw new IllegalStateException("Need at least 1 hider to start the game");
        }

        game.setStatus("active");
        long currentTime = System.currentTimeMillis();
        game.setStartTime(currentTime);
        
        // Start tracking time for all hiders and ensure seekers have no start time
        for (Team team : game.getTeams()) {
            if ("hider".equals(team.getRole())) {
                team.setHiderStartTime(currentTime);
            } else if ("seeker".equals(team.getRole())) {
                team.setHiderStartTime(null); // Ensure seekers don't have start times
            }
        }
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return game;
    }

    public Game endGame(String gameId) {
        Game game = getGame(gameId);
        
        // Accumulate hiding time for all teams that are still hiding
        long currentTime = System.currentTimeMillis();
        for (Team team : game.getTeams()) {
            if ("hider".equals(team.getRole()) && team.getHiderStartTime() != null) {
                long sessionTime = currentTime - team.getHiderStartTime();
                team.addHiderTime(sessionTime);
                team.setHiderStartTime(null);
            }
        }
        
        game.setStatus("ended");
        game.setEndTime(System.currentTimeMillis());
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return game;
    }

    public Game updateGameStatus(String gameId, String status) {
        Game game = getGame(gameId);
        
        String previousStatus = game.getStatus();
        long currentTime = System.currentTimeMillis();
        // If we're transitioning to active from any non-active state (start/resume),
        // ensure there is at least one seeker and one hider configured.
        if ("active".equals(status) && !"active".equals(previousStatus)) {
            long seekers = game.getTeams().stream().filter(t -> "seeker".equals(t.getRole())).count();
            long hiders = game.getTeams().stream().filter(t -> "hider".equals(t.getRole())).count();
            if (seekers < 1) {
                throw new IllegalStateException("You need at least one seeker team to start/resume the game");
            }
            if (hiders < 1) {
                throw new IllegalStateException("You need at least one hider team to start/resume the game");
            }
        }
        // Handle pause time tracking
        if ("paused".equals(status) && "active".equals(previousStatus)) {
            // Game is being paused - accumulate hiding time for all active hiders
            game.setPauseTime(currentTime);
            for (Team team : game.getTeams()) {
                if ("hider".equals(team.getRole()) && team.getHiderStartTime() != null) {
                    long sessionTime = currentTime - team.getHiderStartTime();
                    team.addHiderTime(sessionTime);
                    team.setHiderStartTime(null); // Clear start time since we're pausing
                }
            }
        } else if ("active".equals(status) && "paused".equals(previousStatus)) {
            // Game is being resumed - restart hiding time tracking for all hiders
            if (game.getPauseTime() != null) {
                long pausedDuration = currentTime - game.getPauseTime();
                long totalPaused = game.getTotalPausedDuration() != null ? game.getTotalPausedDuration() : 0L;
                game.setTotalPausedDuration(totalPaused + pausedDuration);
                game.setPauseTime(null);
            }
            // Restart hiding time tracking for all hiders and ensure seekers have no start time
            for (Team team : game.getTeams()) {
                if ("hider".equals(team.getRole())) {
                    team.setHiderStartTime(currentTime);
                } else if ("seeker".equals(team.getRole())) {
                    team.setHiderStartTime(null); // Ensure seekers don't have start times
                }
            }
        }
        
        game.setStatus(status);
        if ("active".equals(status)) {
            if (game.getStartTime() == null) {
                game.setStartTime(currentTime);
            }
        }
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return game;
    }

    public Game nextRound(String gameId) {
        Game game = getGame(gameId);
        
        if (!"paused".equals(game.getStatus())) {
            throw new IllegalStateException("Can only start next round when game is paused");
        }
        
        // Validate that there's at least 1 seeker and 1 hider
        long seekers = game.getTeams().stream().filter(t -> "seeker".equals(t.getRole())).count();
        long hiders = game.getTeams().stream().filter(t -> "hider".equals(t.getRole())).count();
        
        if (seekers < 1) {
            throw new IllegalStateException("Need at least 1 seeker to start the round");
        }
        if (hiders < 1) {
            throw new IllegalStateException("Need at least 1 hider to start the round");
        }
        
        // Increment round and activate game
        game.setRound(game.getRound() + 1);
        game.setStatus("active");
        long currentTime = System.currentTimeMillis();
        game.setStartTime(currentTime);
        
        // Start tracking time for all hiders and ensure seekers have no start time
        for (Team team : game.getTeams()) {
            if ("hider".equals(team.getRole())) {
                team.setHiderStartTime(currentTime);
            } else if ("seeker".equals(team.getRole())) {
                team.setHiderStartTime(null); // Ensure seekers don't have start times
            }
        }
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return game;
    }

    public Team updateTeamTokens(String gameId, String teamId, Integer tokens) {
        Game game = getGame(gameId);
        Team team = gameStore.getTeam(gameId, teamId);
        
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        team.setTokens(tokens);
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Team switchTeamRole(String gameId, String teamId, String role, String foundByTeamId) {
        Game game = getGame(gameId);
        Team team = gameStore.getTeam(gameId, teamId);
        
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        team.setRole(role);
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Team updateTeamLocation(String gameId, String teamId, Location location) {
        Game game = getGame(gameId);
        Team team = gameStore.getTeam(gameId, teamId);
        
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        team.setCurrentLocation(location);
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Team drawChallenge(String gameId, String teamId) {
        Game game = getGame(gameId);
        validateGameIsActive(game);
        
        Team team = gameStore.getTeam(gameId, teamId);
        
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        if (team.getActiveChallenge() != null) {
            throw new IllegalStateException("Team already has an active challenge");
        }
        
        // Check if team is in veto period
        if (team.getVetoEndTime() != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime < team.getVetoEndTime()) {
                throw new IllegalStateException("Team is in veto period");
            } else {
                team.setVetoEndTime(null); // Clear expired veto
            }
        }
        
        Challenge challenge = gameStore.getRandomChallenge(team.getCompletedChallenges());
        if (challenge == null) {
            throw new IllegalStateException("No available challenges");
        }
        
        ActiveChallenge activeChallenge = new ActiveChallenge();
        activeChallenge.setChallenge(challenge);
        activeChallenge.setStartTime(System.currentTimeMillis());
        activeChallenge.setCompleted(false);
        
        team.setActiveChallenge(activeChallenge);
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Team completeChallenge(String gameId, String teamId) {
        Game game = getGame(gameId);
        validateGameIsActive(game);
        
        Team team = gameStore.getTeam(gameId, teamId);
        
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        if (team.getActiveChallenge() == null) {
            throw new IllegalStateException("No active challenge");
        }
        
        ActiveChallenge activeChallenge = team.getActiveChallenge();
        Challenge challenge = activeChallenge.getChallenge();
        
        // Add tokens
        team.setTokens(team.getTokens() + challenge.getTokenReward());
        
        // Mark challenge as completed
        activeChallenge.setCompleted(true);
        team.getCompletedChallenges().add(challenge.getId());
        team.setActiveChallenge(null);
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Team refuseChallenge(String gameId, String teamId) {
        Game game = getGame(gameId);
        validateGameIsActive(game);
        
        Team team = gameStore.getTeam(gameId, teamId);
        
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        if (team.getActiveChallenge() == null) {
            throw new IllegalStateException("No active challenge");
        }
        
        // Seekers no longer get curses for refusing challenges
        // Just apply the veto period
        
        // Set veto period - 5 minutes from now
        team.setVetoEndTime(System.currentTimeMillis() + (5 * 60 * 1000));
        team.setActiveChallenge(null);
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Team markTeamFound(String gameId, String hiderId) {
        Game game = getGame(gameId);
        validateGameIsActive(game);
        
        Team hiderTeam = gameStore.getTeam(gameId, hiderId);
        
        if (hiderTeam == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        if (!"hider".equals(hiderTeam.getRole())) {
            throw new IllegalStateException("Team is not a hider");
        }
        
        // Change hider to seeker
        hiderTeam.setRole("seeker");
        
        // Check if all hiders have been found
        long remainingHiders = game.getTeams().stream()
                .filter(team -> "hider".equals(team.getRole()))
                .count();
        
        if (remainingHiders == 0) { // Only pause when NO hiders remain
            // All hiders found, pause the game for next round setup
            game.setStatus("paused");
        }
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return hiderTeam;
    }

    public Map<String, Object> markTeamFoundWithGameInfo(String gameId, String hiderId) {
        Game game = getGame(gameId);
        validateGameIsActive(game);
        
        Team hiderTeam = gameStore.getTeam(gameId, hiderId);
        
        if (hiderTeam == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        if (!"hider".equals(hiderTeam.getRole())) {
            throw new IllegalStateException("Team is not a hider");
        }
        
        // Accumulate hider time before changing role
        long currentTime = System.currentTimeMillis();
        if (hiderTeam.getHiderStartTime() != null) {
            long sessionTime = currentTime - hiderTeam.getHiderStartTime();
            hiderTeam.addHiderTime(sessionTime);
            hiderTeam.setHiderStartTime(null); // Clear start time since no longer hiding
        }
        
        // Change hider to seeker
        hiderTeam.setRole("seeker");
        
        // Check if all hiders have been found
        long remainingHiders = game.getTeams().stream()
                .filter(team -> "hider".equals(team.getRole()))
                .count();
        
        boolean allHidersFound = remainingHiders == 0; // Only end when NO hiders remain
        
        if (allHidersFound) {
            // All hiders found, accumulate time for remaining hiders and pause the game
            for (Team team : game.getTeams()) {
                if ("hider".equals(team.getRole()) && team.getHiderStartTime() != null) {
                    long sessionTime = currentTime - team.getHiderStartTime();
                    team.addHiderTime(sessionTime);
                    team.setHiderStartTime(null);
                }
            }
            game.setStatus("paused");
        }
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("team", hiderTeam);
        result.put("allHidersFound", allHidersFound);
        result.put("gamePaused", allHidersFound);
        result.put("message", allHidersFound ? 
            hiderTeam.getName() + " found! All hiders found - game paused for next round setup." :
            hiderTeam.getName() + " found! They are now a seeker.");
        
        return result;
    }

    public Team updateTeamRole(String gameId, String teamId, String newRole) {
        Game game = getGame(gameId);
        
        if (!"paused".equals(game.getStatus())) {
            throw new IllegalStateException("Can only change roles when game is paused");
        }
        
        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }
        
        String oldRole = team.getRole();
        
        // If changing from hider to seeker, add accumulated hider time and stop tracking
        if ("hider".equals(oldRole) && "seeker".equals(newRole) && team.getHiderStartTime() != null) {
            long hiderDuration = System.currentTimeMillis() - team.getHiderStartTime();
            team.addHiderTime(hiderDuration);
            team.setHiderStartTime(null);
        }
        
        // If changing from seeker to hider, ensure no leftover start time (safety check)
        if ("seeker".equals(oldRole) && "hider".equals(newRole)) {
            team.setHiderStartTime(null); // Clear any potential leftover start time
        }
        
        // Set the new role
        team.setRole(newRole);
        
        // If changing to hider, set the start time based on game status
        if ("hider".equals(newRole)) {
            if ("active".equals(game.getStatus())) {
                // Game is active, start tracking time immediately
                team.setHiderStartTime(System.currentTimeMillis());
            } else {
                // Game is paused, will be set when round starts
                team.setHiderStartTime(null);
            }
        }
        
        // If changing to seeker, ensure no hider start time (safety check)
        if ("seeker".equals(newRole)) {
            team.setHiderStartTime(null);
        }
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return team;
    }

    public Map<String, Object> getGameStats(String gameId) {
        Game game = getGame(gameId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("gameId", gameId);
        stats.put("round", game.getRound());
        stats.put("status", game.getStatus());
        
        // Calculate team statistics
        List<Map<String, Object>> teamStats = new ArrayList<>();
        for (Team team : game.getTeams()) {
            Map<String, Object> teamStat = new HashMap<>();
            teamStat.put("id", team.getId());
            teamStat.put("name", team.getName());
            teamStat.put("role", team.getRole());
            teamStat.put("tokens", team.getTokens());
            teamStat.put("totalHiderTime", team.getTotalHiderTime());
            teamStat.put("totalHiderTimeFormatted", formatTime(team.getTotalHiderTime()));
            teamStats.add(teamStat);
        }
        stats.put("teams", teamStats);
        
        // Determine winner (team with longest total hider time)
        Team winner = game.getTeams().stream()
                .max((t1, t2) -> Long.compare(t1.getTotalHiderTime(), t2.getTotalHiderTime()))
                .orElse(null);
        
        if (winner != null) {
            Map<String, Object> winnerInfo = new HashMap<>();
            winnerInfo.put("id", winner.getId());
            winnerInfo.put("name", winner.getName());
            winnerInfo.put("totalHiderTime", winner.getTotalHiderTime());
            winnerInfo.put("totalHiderTimeFormatted", formatTime(winner.getTotalHiderTime()));
            stats.put("winner", winnerInfo);
        }
        
        return stats;
    }
    
    private String formatTime(long milliseconds) {
        if (milliseconds <= 0) return "0s";
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public void deleteGame(String gameId) {
        gameStore.deleteGame(gameId);
    }
}
