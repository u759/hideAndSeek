package com.hideandseek.service;

import com.hideandseek.model.Game;
import com.hideandseek.store.GameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
public class GameCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameCleanupService.class);
    private static final Logger gameStatsLogger = LoggerFactory.getLogger("GAME_STATS");
    
    // 12 hours in milliseconds
    private static final long INACTIVITY_THRESHOLD = 12L * 60L * 60L * 1000L;
    
    @Autowired
    private GameStore gameStore;
    
    /**
     * Clean up inactive games every hour
     */
    @Scheduled(fixedRate = 3600000) // Run every hour (3600000 ms)
    public void cleanupInactiveGames() {
        try {
            List<Game> allGames = gameStore.getAllGames();
            long currentTime = System.currentTimeMillis();
            List<String> gamesToDelete = new ArrayList<>();
            
            logger.info("Starting cleanup check for {} games", allGames.size());
            
            for (Game game : allGames) {
                if (game.getLastActivityTime() == null) {
                    // Set activity time for games without it (migration safety)
                    game.setLastActivityTime(currentTime);
                    gameStore.updateGame(game);
                    continue;
                }
                
                long inactiveTime = currentTime - game.getLastActivityTime();
                
                if (inactiveTime > INACTIVITY_THRESHOLD) {
                    gamesToDelete.add(game.getId());
                    logger.info("Marking game {} (code: {}) for deletion after {} hours of inactivity", 
                               game.getId(), game.getCode(), inactiveTime / (60 * 60 * 1000));
                }
            }
            
            // Delete inactive games and clean up all associated data
            for (String gameId : gamesToDelete) {
                try {
                    Game game = gameStore.getGame(gameId);
                    if (game != null) {
                        logger.info("Deleting inactive game {} (code: {}) with {} teams", 
                                   gameId, game.getCode(), game.getTeams().size());
                        
                        // Clean up all game-related data
                        gameStore.deleteGame(gameId);
                        
                        // Note: WebSocket clients will be disconnected when they try to access the deleted game
                    }
                } catch (Exception e) {
                    logger.error("Failed to delete game {}: {}", gameId, e.getMessage());
                }
            }
            
            if (!gamesToDelete.isEmpty()) {
                logger.info("Cleanup completed: deleted {} inactive games", gamesToDelete.size());
            }
            
        } catch (Exception e) {
            logger.error("Error during game cleanup", e);
        }
    }
    
    /**
     * Log active games statistics every 30 minutes for monitoring
     */
    @Scheduled(fixedRate = 1800000) // Run every 30 minutes (1800000 ms)
    public void logActiveGameStats() {
        try {
            List<Game> allGames = gameStore.getAllGames();
            
            int totalGames = allGames.size();
            int activeGames = 0;
            int waitingGames = 0;
            int pausedGames = 0;
            int endedGames = 0;
            int totalTeams = 0;
            
            for (Game game : allGames) {
                totalTeams += game.getTeams().size();
                
                switch (game.getStatus()) {
                    case "active":
                        activeGames++;
                        break;
                    case "waiting":
                        waitingGames++;
                        break;
                    case "paused":
                        pausedGames++;
                        break;
                    case "ended":
                        endedGames++;
                        break;
                }
            }
            
            // Log to dedicated game stats logger (can be configured to write to separate file)
            gameStatsLogger.info("ACTIVE_GAMES_STATS: total={}, active={}, waiting={}, paused={}, ended={}, total_teams={}", 
                                totalGames, activeGames, waitingGames, pausedGames, endedGames, totalTeams);
            
        } catch (Exception e) {
            logger.error("Error logging game statistics", e);
        }
    }
    
    /**
     * Emergency cleanup method that can be called manually if needed
     * Removes all games older than the specified threshold
     */
    public int cleanupGamesOlderThan(long thresholdMs) {
        int deletedCount = 0;
        try {
            List<Game> allGames = gameStore.getAllGames();
            long currentTime = System.currentTimeMillis();
            
            for (Game game : allGames) {
                if (game.getLastActivityTime() != null && 
                    (currentTime - game.getLastActivityTime()) > thresholdMs) {
                    
                    gameStore.deleteGame(game.getId());
                    deletedCount++;
                    logger.info("Emergency cleanup: deleted game {} (code: {})", 
                               game.getId(), game.getCode());
                }
            }
        } catch (Exception e) {
            logger.error("Error during emergency cleanup", e);
        }
        return deletedCount;
    }
}
