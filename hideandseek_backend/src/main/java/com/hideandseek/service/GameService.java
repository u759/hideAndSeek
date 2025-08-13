package com.hideandseek.service;

import com.hideandseek.model.*;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
        game.setStartTime(System.currentTimeMillis());
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
        
        // Switch roles: seekers become hiders, hiders become seekers
        for (Team team : game.getTeams()) {
            if ("seeker".equals(team.getRole())) {
                team.setRole("hider");
            } else if ("hider".equals(team.getRole())) {
                team.setRole("seeker");
            }
        }
        
        game.setRound(game.getRound() + 1);
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
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
        
        return hiderTeam;
    }

    public void deleteGame(String gameId) {
        gameStore.deleteGame(gameId);
    }
}
