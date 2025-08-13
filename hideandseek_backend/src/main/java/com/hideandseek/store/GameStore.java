package com.hideandseek.store;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.Challenge;
import com.hideandseek.model.Curse;
import com.hideandseek.model.ClueType;
import com.hideandseek.model.PurchasedClue;
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
                        Object tokenValue = curseData.get("token_count");
                        if (tokenValue instanceof Integer) {
                            curse.setTokenCount((Integer) tokenValue);
                        } else if (tokenValue instanceof String) {
                            try {
                                curse.setTokenCount(Integer.parseInt((String) tokenValue));
                            } catch (NumberFormatException e) {
                                curse.setTokenCount(0);
                            }
                        } else {
                            curse.setTokenCount(0);
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
        games.put(game.getId(), game);
    }

    public void deleteGame(String gameId) {
        games.remove(gameId);
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
}
