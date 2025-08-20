package com.hideandseek.store;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.Challenge;
import com.hideandseek.model.Curse;
import com.hideandseek.model.ClueType;
import com.hideandseek.model.PurchasedClue;
import com.hideandseek.model.ClueRequest;
import com.hideandseek.model.ClueResponse;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GameStore {
    private static final Logger logger = LoggerFactory.getLogger(GameStore.class);
    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private List<Challenge> challenges = new ArrayList<>();
    private List<Curse> curses = new ArrayList<>();
    private List<ClueType> clueTypes = new ArrayList<>();
    // Store clues per game and per team (seeker)
    private final Map<String, List<PurchasedClue>> teamClueHistory = new ConcurrentHashMap<>();
    // Store async clue requests and responses
    private final Map<String, ClueRequest> clueRequests = new ConcurrentHashMap<>();
    private final Map<String, List<ClueResponse>> clueResponses = new ConcurrentHashMap<>();
    // Store Expo push tokens per game/team (key: gameId:teamId)
    private final Map<String, Set<String>> teamPushTokens = new ConcurrentHashMap<>();
    // Track which team each device token is currently active with (key: token -> gameId:teamId)
    private final Map<String, String> deviceToActiveTeam = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    public GameStore() {
        loadChallengesAndCurses();
        loadClueTypes();
    }

    private void loadChallengesAndCurses() {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("challenges_curses.json");
            if (inputStream != null) {
                Map<String, Object> data = objectMapper.readValue(inputStream, new TypeReference<>() {});
                if (data.containsKey("challenges")) {
                    List<Map<String, Object>> challengeList = (List<Map<String, Object>>) data.get("challenges");
                    for (int i = 0; i < challengeList.size(); i++) {
                        Map<String, Object> challengeData = challengeList.get(i);
                        Challenge challenge = new Challenge();
                        challenge.setId(String.valueOf(i + 1)); // Generate ID
                        challenge.setTitle((String) challengeData.get("title"));
                        challenge.setDescription((String) challengeData.get("description"));
                        // Convert token_count to int
                        Object tokenValue = challengeData.get("token_count");
                        if (tokenValue instanceof Integer) {
                            challenge.setTokenReward((Integer) tokenValue);
                        } else if (tokenValue instanceof String) {
                            try {
                                challenge.setTokenReward(Integer.parseInt((String) tokenValue));
                            } catch (NumberFormatException e) {
                                // Handle non-numeric tokens like "2 x Dice roll" by extracting the number
                                String tokenStr = (String) tokenValue;
                                if (tokenStr.toLowerCase().contains("dice")) {
                                    // Extract number before "x Dice roll"
                                    challenge.setTokenReward(Integer.parseInt(tokenStr.split("\\s+")[0]));
                                } else {
                                    challenge.setTokenReward(1); // Default value
                                }
                            }
                        } else if (tokenValue == null) {
                            challenge.setTokenReward(null); // Allow null for dynamic challenges
                        } else {
                            challenge.setTokenReward(1); // Default value
                        }
                        
                        // Set curse if exists
                        if (challengeData.containsKey("curse")) {
                            Map<String, Object> curseData = (Map<String, Object>) challengeData.get("curse");
                            Curse curse = new Curse();
                            curse.setTitle((String) curseData.get("title"));
                            curse.setDescription((String) curseData.get("description"));
                            // Convert curse token_count to int
                            Object curseTokenValue = curseData.get("token_count");
                            if (curseTokenValue instanceof Integer) {
                                curse.setTokenCount((Integer) curseTokenValue);
                            } else if (curseTokenValue instanceof String) {
                                try {
                                    curse.setTokenCount(Integer.parseInt((String) curseTokenValue));
                                } catch (NumberFormatException e) {
                                    curse.setTokenCount(0); // Default for curses
                                }
                            } else {
                                curse.setTokenCount(0);
                            }
                            challenge.setCurse(curse);
                        }
                        
                        challenges.add(challenge);
                    }
                }
                
                if (data.containsKey("curses")) {
                    List<Map<String, Object>> curseList = (List<Map<String, Object>>) data.get("curses");
                    curses = new ArrayList<>();
                    for (int i = 0; i < curseList.size(); i++) {
                        Map<String, Object> curseData = curseList.get(i);
                        Curse curse = new Curse();
                        curse.setId(String.valueOf(i + 1)); // Assign unique ID
                        curse.setTitle((String) curseData.get("title"));
                        curse.setDescription((String) curseData.get("description"));
                        // Parse price field for curses (not token_count)
                        Object priceValue = curseData.get("price");
                        if (priceValue instanceof Integer) {
                            curse.setTokenCount((Integer) priceValue);
                        } else if (priceValue instanceof String) {
                            try {
                                curse.setTokenCount(Integer.parseInt((String) priceValue));
                            } catch (NumberFormatException e) {
                                curse.setTokenCount(10); // Default curse price
                            }
                        } else {
                            curse.setTokenCount(10); // Default curse price
                        }
                        // Parse time_seconds field for duration
                        Object timeValue = curseData.get("time_seconds");
                        if (timeValue instanceof Integer) {
                            curse.setTimeSeconds((Integer) timeValue);
                        } else if (timeValue instanceof String) {
                            try {
                                curse.setTimeSeconds(Integer.parseInt((String) timeValue));
                            } catch (NumberFormatException e) {
                                curse.setTimeSeconds(null);
                            }
                        } else {
                            curse.setTimeSeconds(null);
                        }
                        // Parse penalty field for time penalties
                        Object penaltyValue = curseData.get("penalty");
                        if (penaltyValue instanceof Integer) {
                            curse.setPenalty((Integer) penaltyValue);
                        } else if (penaltyValue instanceof String) {
                            try {
                                curse.setPenalty(Integer.parseInt((String) penaltyValue));
                            } catch (NumberFormatException e) {
                                curse.setPenalty(null);
                            }
                        } else {
                            curse.setPenalty(null);
                        }
                        curses.add(curse);
                    }
                }
                
                logger.info("Loaded {} challenges and {} curses", challenges.size(), curses.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load challenges and curses", e);
            // Initialize with empty lists if loading fails
            challenges = new ArrayList<>();
            curses = new ArrayList<>();
        }
    }

    private String generateGameCode() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        while (true) {
            sb.setLength(0); // Clear the StringBuilder
            for (int i = 0; i < 6; i++) {
                sb.append(letters.charAt(random.nextInt(letters.length())));
            }
            String generatedCode = sb.toString();
            if (games.values().stream().noneMatch(game -> generatedCode.equals(game.getCode()))) {
                return generatedCode;
            }
        }
    }

    public Game createGame(List<String> teamNames) {
        String gameId = UUID.randomUUID().toString();
        Game game = new Game();
        game.setId(gameId);
        game.setCode(generateGameCode());
        game.setTeams(new ArrayList<>());
        game.setStartTime(null);
        game.setRound(1);
        game.setStatus("waiting");
        
        for (int i = 0; i < teamNames.size(); i++) {
            Team team = new Team();
            team.setId(UUID.randomUUID().toString());
            team.setName(teamNames.get(i));
            team.setRole(i == 0 ? "seeker" : "hider");
            team.setTokens(10); // Starting tokens
            team.setLocation(null);
            team.setCompletedChallenges(new ArrayList<>());
            team.setCompletedCurses(new ArrayList<>());
            team.setActiveChallenge(null);
            team.setActiveCurses(new ArrayList<>());
            team.setAppliedCurses(new ArrayList<>());
            team.setVetoEndTime(null);
            team.setHiderStartTime(null);
            team.setTotalHiderTime(0);
            game.getTeams().add(team);
        }
        
        games.put(gameId, game);
        return game;
    }

    public Game createGameWithRole(List<String> teamNames, String playerRole) {
        String gameId = UUID.randomUUID().toString();
        Game game = new Game();
        game.setId(gameId);
        game.setCode(generateGameCode());
        game.setTeams(new ArrayList<>());
        game.setStartTime(null);
        game.setRound(1);
        game.setStatus("waiting");
        
        // Single player with specified role
        Team team = new Team();
        team.setId(UUID.randomUUID().toString());
        team.setName(teamNames.get(0));
        team.setRole(playerRole);
        team.setTokens(0); // Single player starts with 0 tokens
        team.setLocation(null);
        team.setCompletedChallenges(new ArrayList<>());
        team.setCompletedCurses(new ArrayList<>());
        team.setActiveChallenge(null);
        team.setActiveCurses(new ArrayList<>());
        team.setAppliedCurses(new ArrayList<>());
        team.setVetoEndTime(null);
        team.setHiderStartTime(null);
        team.setTotalHiderTime(0);
        game.getTeams().add(team);
        
        games.put(gameId, game);
        return game;
    }

    public Game getGame(String gameId) {
        return games.get(gameId);
    }

    public Game getGameByCode(String gameCode) {
        return games.values().stream()
                .filter(game -> gameCode.equals(game.getCode()))
                .findFirst()
                .orElse(null);
    }

    public List<Game> getAllGames() {
        return new ArrayList<>(games.values());
    }

    public void updateGame(Game game) {
        if (game != null) {
            game.updateActivity(); // Update last activity timestamp
            games.put(game.getId(), game);
        }
    }

    public void deleteGame(String gameId) {
        // Remove the game itself
        games.remove(gameId);
        
        // Clean up all associated data to prevent memory leaks
        teamClueHistory.entrySet().removeIf(entry -> entry.getKey().startsWith(gameId + ":"));
        clueRequests.entrySet().removeIf(entry -> entry.getKey().startsWith(gameId + ":"));
        clueResponses.entrySet().removeIf(entry -> entry.getKey().startsWith(gameId + ":"));
        teamPushTokens.entrySet().removeIf(entry -> entry.getKey().startsWith(gameId + ":"));
        
        // Clean up device-to-team mappings for this game
        deviceToActiveTeam.entrySet().removeIf(entry -> entry.getValue().startsWith(gameId + ":"));
        
        logger.info("Cleaned up all data for game {}", gameId);
    }

    public Team getTeam(String gameId, String teamId) {
        Game game = getGame(gameId);
        if (game != null) {
            return game.getTeams().stream()
                    .filter(team -> team.getId().equals(teamId))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public Challenge getRandomChallenge(List<String> completedChallengeIds) {
        List<Challenge> availableChallenges = challenges.stream()
                .filter(challenge -> !completedChallengeIds.contains(challenge.getId()))
                .toList();
        
        if (availableChallenges.isEmpty()) {
            return null;
        }
        
        return availableChallenges.get(random.nextInt(availableChallenges.size()));
    }

    public Curse getRandomCurse() {
        if (curses.isEmpty()) {
            return null;
        }
        
        return curses.get(random.nextInt(curses.size()));
    }

    public List<Challenge> getAllChallenges() {
        return new ArrayList<>(challenges);
    }

    public List<Curse> getAllCurses() {
        return new ArrayList<>(curses);
    }

    // New method: get random curse for a team, enforcing repeat-prevention
    public Curse getRandomCurseForTeam(Team team) {
        List<String> completedCurseIds = team.getCompletedCurses();
        List<Curse> availableCurses = curses.stream()
                .filter(curse -> curse.getId() != null && !completedCurseIds.contains(curse.getId()))
                .toList();

        // If all curses have been used, reset completedCurses for the team
        if (availableCurses.isEmpty()) {
            team.setCompletedCurses(new ArrayList<>());
            availableCurses = new ArrayList<>(curses);
        }

        Curse selected = availableCurses.get(random.nextInt(availableCurses.size()));
        team.getCompletedCurses().add(selected.getId());
        return selected;
    }

    private void loadClueTypes() {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("clue_types.json");
            if (inputStream != null) {
                Map<String, Object> data = objectMapper.readValue(inputStream, new TypeReference<>() {});
                if (data.containsKey("clue_types")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> clueTypeList = (List<Map<String, Object>>) data.get("clue_types");
                    for (Map<String, Object> clueTypeData : clueTypeList) {
                        ClueType clueType = new ClueType();
                        clueType.setId((String) clueTypeData.get("id"));
                        clueType.setName((String) clueTypeData.get("name"));
                        clueType.setDescription((String) clueTypeData.get("description"));
                        clueType.setCost((Integer) clueTypeData.get("cost"));
                        clueTypes.add(clueType);
                    }
                }
                logger.info("Loaded {} clue types", clueTypes.size());
            } else {
                logger.warn("clue_types.json not found in resources");
            }
        } catch (Exception e) {
            logger.error("Failed to load clue types", e);
        }
    }

    public List<ClueType> getAllClueTypes() {
        return new ArrayList<>(clueTypes);
    }

    public ClueType getClueTypeById(String id) {
        return clueTypes.stream()
                .filter(clueType -> clueType.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public void addPurchasedClue(PurchasedClue purchasedClue) {
    String key = purchasedClue.getGameId() + ":" + purchasedClue.getTeamId();
    teamClueHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(purchasedClue);
    }

    public List<PurchasedClue> getClueHistory(String gameId) {
    throw new UnsupportedOperationException("Use getClueHistoryForTeam instead");
    }

    public void addClueToHistory(String gameId, PurchasedClue clue) {
        String key = clue.getGameId() + ":" + clue.getTeamId();
        teamClueHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(clue);
    }

    // New method: get clue history for a specific team in a game
    public List<PurchasedClue> getClueHistoryForTeam(String gameId, String teamId) {
        String key = gameId + ":" + teamId;
        return teamClueHistory.getOrDefault(key, new ArrayList<>());
    }
    
    // Async clue request methods
    public void addClueRequest(ClueRequest request) {
        clueRequests.put(request.getId(), request);
    }
    
    public ClueRequest getClueRequest(String requestId) {
        return clueRequests.get(requestId);
    }
    
    public List<ClueRequest> getPendingClueRequestsForTeam(String gameId, String teamId) {
        return clueRequests.values().stream()
                .filter(req -> req.getGameId().equals(gameId) && 
                              req.getTargetHiderTeamId().equals(teamId) && 
                              "pending".equals(req.getStatus()) &&
                              !req.isExpired())
                .collect(java.util.stream.Collectors.toList());
    }
    
    public void updateClueRequest(ClueRequest request) {
        clueRequests.put(request.getId(), request);
    }
    
    public List<ClueRequest> getExpiredClueRequests() {
        return clueRequests.values().stream()
                .filter(req -> "pending".equals(req.getStatus()) && req.isExpired())
                .collect(java.util.stream.Collectors.toList());
    }
    
    public void addClueResponse(ClueResponse response) {
        String key = response.getRequestingTeamId() + ":" + response.getGameId();
        clueResponses.computeIfAbsent(key, k -> new ArrayList<>()).add(response);
    }
    
    public List<ClueResponse> getUndeliveredClueResponsesForTeam(String gameId, String teamId) {
        String key = teamId + ":" + gameId;
        return clueResponses.getOrDefault(key, new ArrayList<>()).stream()
                .filter(response -> !response.isDelivered())
                .collect(java.util.stream.Collectors.toList());
    }
    
    public void markClueResponseAsDelivered(String requestId, String teamId, String gameId) {
        String key = teamId + ":" + gameId;
        List<ClueResponse> responses = clueResponses.get(key);
        if (responses != null) {
            responses.stream()
                    .filter(response -> response.getRequestId().equals(requestId))
                    .forEach(response -> response.setDelivered(true));
        }
    }

    // Push token management
    public void registerPushToken(String gameId, String teamId, String token) {
        if (gameId == null || teamId == null || token == null || token.isBlank()) {
            logger.warn("Invalid push token registration: gameId={}, teamId={}, token={}", gameId, teamId, token);
            return;
        }
        
        // Clean up previous registration for this device token
        String previousTeamKey = deviceToActiveTeam.get(token);
        if (previousTeamKey != null) {
            Set<String> previousTokens = teamPushTokens.get(previousTeamKey);
            if (previousTokens != null) {
                previousTokens.remove(token);
                logger.info("Removed token {} from previous team {}", token, previousTeamKey);
                // Clean up empty token sets
                if (previousTokens.isEmpty()) {
                    teamPushTokens.remove(previousTeamKey);
                }
            }
        }
        
        // Register token with new team
        String newTeamKey = gameId + ":" + teamId;
        teamPushTokens.computeIfAbsent(newTeamKey, k -> ConcurrentHashMap.newKeySet()).add(token);
        deviceToActiveTeam.put(token, newTeamKey);
        
        logger.info("Registered push token for game {} team {}: {} (cleaned up previous: {})", 
                gameId, teamId, token, previousTeamKey != null ? previousTeamKey : "none");
    }
    
    public void unregisterPushToken(String gameId, String teamId, String token) {
        if (gameId == null || teamId == null || token == null || token.isBlank()) {
            logger.warn("Invalid push token unregistration: gameId={}, teamId={}, token={}", gameId, teamId, token);
            return;
        }
        
        String teamKey = gameId + ":" + teamId;
        Set<String> tokens = teamPushTokens.get(teamKey);
        if (tokens != null) {
            tokens.remove(token);
            logger.info("Unregistered push token from game {} team {}: {}", gameId, teamId, token);
            // Clean up empty token sets
            if (tokens.isEmpty()) {
                teamPushTokens.remove(teamKey);
            }
        }
        
        // Remove from active device tracking if this was the active team
        String activeTeamKey = deviceToActiveTeam.get(token);
        if (teamKey.equals(activeTeamKey)) {
            deviceToActiveTeam.remove(token);
            logger.info("Removed token {} from active device tracking", token);
        }
    }
    
    public void unregisterAllTokensForDevice(String token) {
        if (token == null || token.isBlank()) {
            logger.warn("Invalid token for device cleanup: {}", token);
            return;
        }
        
        // Remove from active tracking
        String activeTeamKey = deviceToActiveTeam.remove(token);
        
        // Remove from all teams in all games
        int removedCount = 0;
        for (Map.Entry<String, Set<String>> entry : teamPushTokens.entrySet()) {
            if (entry.getValue().remove(token)) {
                removedCount++;
                // Clean up empty token sets
                if (entry.getValue().isEmpty()) {
                    teamPushTokens.remove(entry.getKey());
                }
            }
        }
        
        logger.info("Removed token {} from {} teams (was active in: {})", 
                token, removedCount, activeTeamKey != null ? activeTeamKey : "none");
    }

    public Set<String> getPushTokens(String gameId, String teamId) {
        if (gameId == null || teamId == null) {
            logger.warn("Invalid push token lookup: gameId={}, teamId={}", gameId, teamId);
            return Set.of();
        }
        
        String key = gameId + ":" + teamId;
        Set<String> tokens = teamPushTokens.get(key);
        Set<String> result = tokens != null ? tokens : Set.of();
        
        if (result.isEmpty()) {
            logger.warn("No push tokens found for game {} team {}", gameId, teamId);
        } else {
            logger.debug("Found {} push tokens for game {} team {}", result.size(), gameId, teamId);
        }
        
        return result;
    }
    
    // Find closest hider team to requesting seeker team
    public Team getClosestHiderTeam(String gameId, String requestingTeamId) {
        Game game = getGame(gameId);
        if (game == null) return null;
        
        Team requestingTeam = getTeam(gameId, requestingTeamId);
        if (requestingTeam == null || requestingTeam.getLocation() == null) return null;
        
        return game.getTeams().stream()
                .filter(team -> "hider".equals(team.getRole()) && team.getLocation() != null)
                .min((h1, h2) -> {
                    double dist1 = calculateDistance(requestingTeam.getLocation(), h1.getLocation());
                    double dist2 = calculateDistance(requestingTeam.getLocation(), h2.getLocation());
                    return Double.compare(dist1, dist2);
                })
                .orElse(null);
    }
    
    // Utility method to calculate distance between team locations
    private double calculateDistance(Team.TeamLocation loc1, Team.TeamLocation loc2) {
        double lat1 = Math.toRadians(loc1.getLatitude());
        double lon1 = Math.toRadians(loc1.getLongitude());
        double lat2 = Math.toRadians(loc2.getLatitude());
        double lon2 = Math.toRadians(loc2.getLongitude());

        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;

        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double radius = 6371000; // Earth's radius in meters

        return radius * c;
    }
}
