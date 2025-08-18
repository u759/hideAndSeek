package com.hideandseek.service;

import com.hideandseek.model.*;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CurseService {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameWebSocketHandler webSocketHandler;

    public Map<String, Object> curseTeam(String gameId, String seekerTeamId, String targetTeamId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot curse teams when game is not active");
        }

        Team seekerTeam = gameStore.getTeam(gameId, seekerTeamId);
        if (seekerTeam == null) {
            throw new IllegalArgumentException("Seeker team not found");
        }

        if (!"seeker".equals(seekerTeam.getRole())) {
            throw new IllegalStateException("Only seekers can curse other teams");
        }

        Team targetTeam = gameStore.getTeam(gameId, targetTeamId);
        if (targetTeam == null) {
            throw new IllegalArgumentException("Target team not found");
        }

        if (!"hider".equals(targetTeam.getRole())) {
            throw new IllegalStateException("Can only curse hider teams");
        }

        // Check if target team already has an active curse
        long currentTime = System.currentTimeMillis();
        targetTeam.getActiveCurses().removeIf(activeCurse -> activeCurse.getEndTime() <= currentTime);
        
        if (!targetTeam.getActiveCurses().isEmpty()) {
            throw new IllegalStateException("Target team already has an active curse");
        }

        // Get a random curse
        Curse curse = gameStore.getRandomCurse();
        if (curse == null) {
            throw new IllegalStateException("No curses available");
        }

        // Check if seeker has enough tokens
        if (seekerTeam.getTokens() < curse.getTokenCount()) {
            throw new IllegalStateException("Not enough tokens to apply this curse. Need " + curse.getTokenCount() + " tokens.");
        }

        // Apply the curse
        ActiveCurse activeCurse = new ActiveCurse();
        activeCurse.setCurse(curse);
        activeCurse.setStartTime(currentTime);
        // Use timeSeconds field for proper duration, fallback to token count if not available
        int durationMinutes = curse.getTimeSeconds() != null ? 
            curse.getTimeSeconds() / 60 : 
            curse.getTokenCount(); // Fallback to token count as minutes
        activeCurse.setDuration(durationMinutes);
        targetTeam.getActiveCurses().add(activeCurse);

        // Record the applied curse for the seeker
        AppliedCurse appliedCurse = new AppliedCurse();
        appliedCurse.setCurse(curse);
        appliedCurse.setTargetTeamId(targetTeam.getId());
        appliedCurse.setTargetTeamName(targetTeam.getName());
        appliedCurse.setStartTime(currentTime);
        appliedCurse.setDuration(durationMinutes);
        seekerTeam.getAppliedCurses().add(appliedCurse);

        // Deduct tokens from seeker
        seekerTeam.setTokens(seekerTeam.getTokens() - curse.getTokenCount());

        // Update game
        gameStore.updateGame(game);

        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Map<String, Object> result = new HashMap<>();
        result.put("seekerTeam", seekerTeam);
        result.put("targetTeam", targetTeam);
        result.put("appliedCurse", appliedCurse);
        result.put("curse", curse);
        return result;
    }

    public List<Team> getAvailableTargets(String gameId, String seekerTeamId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        long currentTime = System.currentTimeMillis();
        
        return game.getTeams().stream()
                .filter(team -> !"seeker".equals(team.getRole())) // Only hider teams
                .filter(team -> !team.getId().equals(seekerTeamId)) // Not the seeker themselves
                .filter(team -> {
                    // Remove expired curses and check if team has no active curses
                    team.getActiveCurses().removeIf(activeCurse -> activeCurse.getEndTime() <= currentTime);
                    return team.getActiveCurses().isEmpty();
                })
                .toList();
    }

    public Map<String, Object> markCurseCompleted(String gameId, String hiderTeamId, String curseId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) throw new IllegalArgumentException("Game not found");

        Team team = gameStore.getTeam(gameId, hiderTeamId);
        if (team == null) throw new IllegalArgumentException("Team not found");
        if (!"hider".equals(team.getRole())) throw new IllegalStateException("Only hiders can complete curses");

        long now = System.currentTimeMillis();
        // Clean expired first
        team.getActiveCurses().removeIf(ac -> ac.getEndTime() <= now);
        var active = team.getActiveCurses().stream()
                .filter(ac -> ac.getCurse() != null && ac.getCurse().getId().equals(curseId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Active curse not found or already expired"));

        if (active.isCompleted()) {
            throw new IllegalStateException("Curse already marked as completed");
        }
        active.setCompleted(true);
        active.setCompletedAt(now);

        gameStore.updateGame(game);
        webSocketHandler.broadcastToGame(gameId, game);
        return Map.of(
                "team", team,
                "activeCurse", active
        );
    }
}
