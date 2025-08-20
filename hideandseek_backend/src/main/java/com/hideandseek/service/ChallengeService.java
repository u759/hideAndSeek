package com.hideandseek.service;

import com.hideandseek.model.*;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChallengeService {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameWebSocketHandler webSocketHandler;

    public Map<String, Object> getAllChallengesAndCurses() {
        List<Challenge> challenges = gameStore.getAllChallenges();
        List<Curse> curses = gameStore.getAllCurses();
        
        Map<String, Object> result = new HashMap<>();
        result.put("challenges", challenges);
        result.put("curses", curses);
        
        return result;
    }

    public Map<String, Object> drawCard(String gameId, String teamId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Validate game is active before allowing challenge draws
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot draw challenges when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        // Check if team is a hider (hiders cannot do challenges)
        if ("hider".equals(team.getRole())) {
            throw new IllegalStateException("Hiders cannot draw challenges. Only seekers can access challenges.");
        }

        // Enforce one active challenge at a time
        if (team.getActiveChallenge() != null) {
            throw new IllegalStateException("You already have an active challenge. Complete or veto it before drawing another.");
        }

        // Check if team is in veto period
        if (team.getVetoEndTime() != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime < team.getVetoEndTime()) {
                long remainingMs = team.getVetoEndTime() - currentTime;
                long remainingMinutes = (remainingMs / (60 * 1000)) + 1;
                throw new IllegalStateException(String.format("You must wait %d more minutes before drawing another card.", 
                    remainingMinutes));
            } else {
                team.setVetoEndTime(null); // Clear expired veto
            }
        }

        // Get available challenges not completed by team (tolerate legacy title-based entries)
        List<Challenge> allChallenges = gameStore.getAllChallenges();
        Set<String> completed = new HashSet<>(team.getCompletedChallenges());
        List<Challenge> availableChallenges = new ArrayList<>();
        for (Challenge challenge : allChallenges) {
            String id = challenge.getId();
            String title = challenge.getTitle();
            if (!completed.contains(id) && !completed.contains(title)) {
                availableChallenges.add(challenge);
            }
        }

        // Seekers can only draw challenges (no curses anymore)
        if (availableChallenges.isEmpty()) {
            // If all challenges have been completed, reset the completed list and allow drawing again
            team.setCompletedChallenges(new ArrayList<>());
            availableChallenges = new ArrayList<>(allChallenges);
            gameStore.updateGame(game);
        }

    Challenge drawnChallenge = availableChallenges.get(new Random().nextInt(availableChallenges.size()));

    // Set active challenge on the team for persistence across navigation/logins
    ActiveChallenge active = new ActiveChallenge();
    active.setChallenge(drawnChallenge);
    active.setStartTime(System.currentTimeMillis());
    active.setCompleted(false);
    team.setActiveChallenge(active);
    gameStore.updateGame(game);
    // Broadcast to clients so UI hydrates active challenge
    webSocketHandler.broadcastToGame(gameId, game);

    // Return a predictable JSON shape for the frontend, include token_count field
    Map<String, Object> cardMap = new HashMap<>();
    cardMap.put("id", drawnChallenge.getId());
    cardMap.put("title", drawnChallenge.getTitle());
    cardMap.put("description", drawnChallenge.getDescription());
    cardMap.put("token_count", drawnChallenge.getTokenReward());

    Map<String, Object> result = new HashMap<>();
    result.put("card", cardMap);
    result.put("type", "challenge");
    result.put("remainingChallenges", availableChallenges.size() - 1);
    return result;
    }

    public Map<String, Object> completeChallenge(String gameId, String teamId, String challengeTitle) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Validate game is active before allowing challenge completion
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot complete challenges when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        // Check if team is a hider (hiders cannot do challenges)
        if ("hider".equals(team.getRole())) {
            throw new IllegalStateException("Hiders cannot complete challenges. Only seekers can access challenges.");
        }

        // Require an active challenge
        ActiveChallenge active = team.getActiveChallenge();
        if (active == null || active.getChallenge() == null) {
            throw new IllegalStateException("No active challenge to complete");
        }
        // Optional: ensure the provided title matches the active challenge
        if (challengeTitle != null && !challengeTitle.equals(active.getChallenge().getTitle())) {
            throw new IllegalStateException("Provided challenge does not match the active challenge");
        }

        Challenge challenge = active.getChallenge();
        Integer tokensEarned = challenge.getTokenReward();

        // For dynamic challenges (null token_count), require custom token input
        if (tokensEarned == null) {
            throw new IllegalStateException("This is a dynamic challenge. Use the custom token completion endpoint.");
        }

        // Update team state: tokens, completed list (normalize to use ID), clear active
        team.setTokens(team.getTokens() + tokensEarned);
        List<String> completed = team.getCompletedChallenges();
        boolean hasId = completed.contains(challenge.getId());
        boolean hasTitle = completed.contains(challenge.getTitle());
        if (!hasId) {
            completed.add(challenge.getId());
        }
        // Optionally remove legacy title entry to normalize
        if (hasTitle) {
            completed.remove(challenge.getTitle());
        }
        team.setActiveChallenge(null);

        gameStore.updateGame(game);
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Map<String, Object> result = new HashMap<>();
        result.put("tokensEarned", tokensEarned);
        result.put("newTokenBalance", team.getTokens());
        result.put("challenge", challenge);
        result.put("message", String.format("Challenge completed! Earned %d tokens.", tokensEarned));
        return result;
    }

    public Map<String, Object> completeChallengeWithCustomTokens(String gameId, String teamId, String challengeTitle, Integer customTokens) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Validate game is active before allowing challenge completion
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot complete challenges when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        // Check if team is a hider (hiders cannot do challenges)
        if ("hider".equals(team.getRole())) {
            throw new IllegalStateException("Hiders cannot complete challenges. Only seekers can access challenges.");
        }

        // Require an active challenge
        ActiveChallenge active = team.getActiveChallenge();
        if (active == null || active.getChallenge() == null) {
            throw new IllegalStateException("No active challenge to complete");
        }
        // Optional: ensure the provided title matches the active challenge
        if (challengeTitle != null && !challengeTitle.equals(active.getChallenge().getTitle())) {
            throw new IllegalStateException("Provided challenge does not match the active challenge");
        }

        Challenge challenge = active.getChallenge();
        
        // Validate that this is a dynamic challenge (null token_count)
        if (challenge.getTokenReward() != null) {
            throw new IllegalStateException("This is not a dynamic challenge. Use the regular completion endpoint.");
        }

        // Validate custom tokens
        if (customTokens == null || customTokens < 0) {
            throw new IllegalArgumentException("Custom tokens must be a non-negative number");
        }

        Integer tokensEarned = customTokens;

        // Update team state: tokens, completed list (normalize to use ID), clear active
        team.setTokens(team.getTokens() + tokensEarned);
        List<String> completed = team.getCompletedChallenges();
        boolean hasId = completed.contains(challenge.getId());
        boolean hasTitle = completed.contains(challenge.getTitle());
        if (!hasId) {
            completed.add(challenge.getId());
        }
        // Optionally remove legacy title entry to normalize
        if (hasTitle) {
            completed.remove(challenge.getTitle());
        }
        team.setActiveChallenge(null);

        gameStore.updateGame(game);
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Map<String, Object> result = new HashMap<>();
        result.put("tokensEarned", tokensEarned);
        result.put("newTokenBalance", team.getTokens());
        result.put("challenge", challenge);
        result.put("message", String.format("Challenge completed! Earned %d tokens.", tokensEarned));
        return result;
    }

    public Map<String, Object> vetoChallenge(String gameId, String teamId, String challengeTitle) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Validate game is active before allowing challenge veto
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot veto challenges when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        // Check if team is a hider (hiders cannot do challenges)
        if ("hider".equals(team.getRole())) {
            throw new IllegalStateException("Hiders cannot veto challenges. Only seekers can access challenges.");
        }

        // Require an active challenge
        ActiveChallenge active = team.getActiveChallenge();
        if (active == null || active.getChallenge() == null) {
            throw new IllegalStateException("No active challenge to veto");
        }
        // Optional: ensure the provided title matches the active challenge
        if (challengeTitle != null && !challengeTitle.equals(active.getChallenge().getTitle())) {
            throw new IllegalStateException("Provided challenge does not match the active challenge");
        }

        // Set 5-minute veto penalty and clear active
        long vetoEnd = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes from now
        team.setVetoEndTime(vetoEnd);
        team.setActiveChallenge(null);
        
        gameStore.updateGame(game);
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Challenge vetoed. 5-minute penalty applied.");
        result.put("vetoEndTime", vetoEnd);
        result.put("penaltyMinutes", 5);
        return result;
    }
}
