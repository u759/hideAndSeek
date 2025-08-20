package com.hideandseek.service;

import com.hideandseek.model.ClueRequest;
import com.hideandseek.model.ClueResponse;
import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.PurchasedClue;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@Service
public class ClueMaintenanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClueMaintenanceService.class);
    
    @Autowired
    private GameStore gameStore;
    
    @Autowired
    private GameWebSocketHandler webSocketHandler;
    
    @Autowired
    private PushService pushService;
    
    // Run every minute to check for expired clue requests
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredClueRequests() {
        try {
            logger.debug("Checking for expired clue requests...");
            
            List<ClueRequest> expiredRequests = gameStore.getExpiredClueRequests();
            for (ClueRequest request : expiredRequests) {
                processExpiredClueRequest(request);
                logger.info("Processed expired clue request {} for clue type {}", request.getId(), request.getClueTypeId());
            }
            
            if (!expiredRequests.isEmpty()) {
                logger.info("Processed {} expired clue requests", expiredRequests.size());
            }
            
        } catch (Exception e) {
            logger.error("Error during clue request cleanup", e);
        }
    }
    
    private void processExpiredClueRequest(ClueRequest request) {
        // Mark request as expired
        request.setStatus("expired");
        gameStore.updateClueRequest(request);
        
        // For selfie and closest-landmark clues, provide exact location as reward
        if ("selfie".equals(request.getClueTypeId()) || "closest-landmark".equals(request.getClueTypeId())) {
            provideExactLocationReward(request);
        }
    }
    
    private void provideExactLocationReward(ClueRequest expiredRequest) {
        try {
            Game game = gameStore.getGame(expiredRequest.getGameId());
            if (game == null || !"active".equals(game.getStatus())) {
                logger.warn("Game not found or not active for expired clue request {}", expiredRequest.getId());
                return;
            }
            
            Team requestingTeam = gameStore.getTeam(expiredRequest.getGameId(), expiredRequest.getRequestingTeamId());
            Team targetHiderTeam = gameStore.getTeam(expiredRequest.getGameId(), expiredRequest.getTargetHiderTeamId());
            
            if (requestingTeam == null || targetHiderTeam == null) {
                logger.warn("Teams not found for expired clue request {}", expiredRequest.getId());
                return;
            }
            
            if (targetHiderTeam.getLocation() == null) {
                logger.warn("Target hider team has no location for expired clue request {}", expiredRequest.getId());
                return;
            }
            
            // Create exact location clue as reward
            String rewardClueId = UUID.randomUUID().toString();
            String clueText = String.format("TIMEOUT REWARD: Exact location of %s: %.6f, %.6f (Hider failed to respond to %s request in time)", 
                    targetHiderTeam.getName(),
                    targetHiderTeam.getLocation().getLatitude(),
                    targetHiderTeam.getLocation().getLongitude(),
                    expiredRequest.getClueTypeName());
            
            PurchasedClue rewardClue = new PurchasedClue(
                    rewardClueId, "exact-location", requestingTeam.getId(), game.getId(),
                    clueText, 0, "completed", null, "location", targetHiderTeam.getId(),
                    targetHiderTeam.getLocation().getLatitude(), targetHiderTeam.getLocation().getLongitude(),
                    targetHiderTeam.getName()
            );
            
            // Add to clue history
            gameStore.addClueToHistory(game.getId(), rewardClue);
            
            // Find the original purchased clue and update its status
            List<PurchasedClue> clueHistory = gameStore.getClueHistoryForTeam(game.getId(), requestingTeam.getId());
            PurchasedClue originalClue = clueHistory.stream()
                    .filter(clue -> expiredRequest.getId().equals(clue.getRequestId()))
                    .findFirst()
                    .orElse(null);
            
            if (originalClue != null) {
                originalClue.setStatus("expired");
                originalClue.setClueText("Request expired - automatic location reward provided");
                logger.info("Updated original clue {} status to expired", originalClue.getId());
            } else {
                logger.warn("Could not find original clue for expired request {}", expiredRequest.getId());
            }
            
            // Create and broadcast ClueResponse for the exact location reward
            ClueResponse automaticResponse = new ClueResponse(
                    expiredRequest.getId(),
                    expiredRequest.getGameId(),
                    expiredRequest.getTargetHiderTeamId(),
                    expiredRequest.getRequestingTeamId(),
                    "exact-location", // The reward clue type
                    "automatic", // Response type indicates this was automatic
                    rewardClue.getClueText()
            );
            gameStore.addClueResponse(automaticResponse);
            
            // Broadcast clue response to requesting team (seekers)
            Map<String, Object> responseInfo = new HashMap<>();
            responseInfo.put("requestId", expiredRequest.getId());
            responseInfo.put("clueTypeId", "exact-location");
            responseInfo.put("responseType", "automatic");
            responseInfo.put("responseData", rewardClue.getClueText());
            responseInfo.put("timestamp", automaticResponse.getResponseTimestamp());
            
            webSocketHandler.broadcastClueResponse(game.getId(), expiredRequest.getRequestingTeamId(), responseInfo);
            
            // Broadcast general game update
            webSocketHandler.broadcastToGame(game.getId(), game);
            
            // Send push notifications
            pushService.notifyClueTimeoutReward(
                    game.getId(), 
                    requestingTeam.getId(), 
                    targetHiderTeam.getId(),
                    expiredRequest.getClueTypeName(),
                    targetHiderTeam.getName()
            );
            
            logger.info("Provided exact location reward for expired {} clue request {} from team {} targeting team {}", 
                    expiredRequest.getClueTypeName(), expiredRequest.getId(), requestingTeam.getName(), targetHiderTeam.getName());
            
        } catch (Exception e) {
            logger.error("Error providing exact location reward for expired clue request {}", expiredRequest.getId(), e);
        }
    }
}
