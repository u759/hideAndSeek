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

        // Get available challenges not completed by team
        List<Challenge> allChallenges = gameStore.getAllChallenges();
        List<Challenge> availableChallenges = new ArrayList<>();
        for (Challenge challenge : allChallenges) {
            if (!team.getCompletedChallenges().contains(challenge.getId())) {
                availableChallenges.add(challenge);
            }
        }

        // Seekers can only draw challenges (no curses anymore)
        if (availableChallenges.isEmpty()) {
            throw new IllegalStateException("No more challenges available for this team.");
        }

    Challenge drawnChallenge = availableChallenges.get(new Random().nextInt(availableChallenges.size()));

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

        // Find the challenge
        Challenge challenge = gameStore.getAllChallenges().stream()
                .filter(c -> c.getTitle().equals(challengeTitle))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Calculate tokens earned
        int tokensEarned = challenge.getTokenReward();

        // Update team
        team.setTokens(team.getTokens() + tokensEarned);
        if (!team.getCompletedChallenges().contains(challenge.getTitle())) {
            team.getCompletedChallenges().add(challenge.getTitle());
        }
        
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

        // Set 5-minute veto penalty
        team.setVetoEndTime(System.currentTimeMillis() + (5 * 60 * 1000)); // 5 minutes from now
        
        gameStore.updateGame(game);
        
        // Broadcast to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Challenge vetoed. 5-minute penalty applied.");
        result.put("vetoEndTime", System.currentTimeMillis() + (5 * 60 * 1000));
        result.put("penaltyMinutes", 5);
        
        return result;
    }
}
