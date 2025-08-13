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

        // If no challenges left, only draw curse
        boolean onlyCurse = availableChallenges.isEmpty();
        Object drawnCard;
        String cardType;
        if (onlyCurse) {
            drawnCard = gameStore.getRandomCurseForTeam(team);
            cardType = "curse";
        } else {
            // 50/50 chance to draw challenge or curse
            if (new Random().nextBoolean()) {
                drawnCard = availableChallenges.get(new Random().nextInt(availableChallenges.size()));
                cardType = "challenge";
            } else {
                drawnCard = gameStore.getRandomCurseForTeam(team);
                cardType = "curse";
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("card", drawnCard);
        result.put("type", cardType);
        result.put("remainingChallenges", availableChallenges.size() - (cardType.equals("challenge") ? 1 : 0));
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
        if (!team.getCompletedChallenges().contains(challenge.getId())) {
            team.getCompletedChallenges().add(challenge.getId());
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
