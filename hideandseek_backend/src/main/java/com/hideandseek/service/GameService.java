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
        game.setStatus("active");
        long currentTime = System.currentTimeMillis();
        game.setStartTime(currentTime);
        
        // Start tracking time for all hiders
        for (Team team : game.getTeams()) {
            if ("hider".equals(team.getRole())) {
                team.setHiderStartTime(currentTime);
            }
        }
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return game;
    }

    public Game endGame(String gameId) {
        Game game = getGame(gameId);
        game.setStatus("ended");
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return game;
    }

    public Game updateGameStatus(String gameId, String status) {
        Game game = getGame(gameId);
        
        String previousStatus = game.getStatus();
        long currentTime = System.currentTimeMillis();

        // Handle pause time tracking
        if ("paused".equals(status) && "active".equals(previousStatus)) {
            // Game is being paused
            game.setPauseTime(currentTime);
        } else if ("active".equals(status) && "paused".equals(previousStatus)) {
            // Game is being resumed
            if (game.getPauseTime() != null) {
                long pausedDuration = currentTime - game.getPauseTime();
                long totalPaused = game.getTotalPausedDuration() != null ? game.getTotalPausedDuration() : 0L;
                game.setTotalPausedDuration(totalPaused + pausedDuration);
                game.setPauseTime(null);
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
        
        // Start tracking time for all hiders
        for (Team team : game.getTeams()) {
            if ("hider".equals(team.getRole())) {
                team.setHiderStartTime(currentTime);
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
        
        // Apply curse if challenge has one
        Challenge challenge = team.getActiveChallenge().getChallenge();
        if (challenge.getCurse() != null) {
            ActiveCurse activeCurse = new ActiveCurse();
            activeCurse.setCurse(challenge.getCurse());
            activeCurse.setStartTime(System.currentTimeMillis());
            activeCurse.setDuration(5); // 5 minutes
            team.getActiveCurses().add(activeCurse);
        }
        
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
                .count() - 1; // Subtract 1 for the hider we just converted
        
        if (remainingHiders == 0) {
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
                .count() - 1; // Subtract 1 for the hider we just converted
        
        boolean allHidersFound = remainingHiders == 0;
        
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
        
        // If changing from hider to seeker, add accumulated hider time
        if ("hider".equals(oldRole) && "seeker".equals(newRole) && team.getHiderStartTime() != null) {
            long hiderDuration = System.currentTimeMillis() - team.getHiderStartTime();
            team.addHiderTime(hiderDuration);
            team.setHiderStartTime(null);
        }
        
        // Set the new role
        team.setRole(newRole);
        
        // If changing to hider, set the start time (will be updated when round actually starts)
        if ("hider".equals(newRole)) {
            team.setHiderStartTime(null); // Will be set when round starts
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
