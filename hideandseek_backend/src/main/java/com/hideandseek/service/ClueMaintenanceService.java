package com.hideandseek.service;

import com.hideandseek.model.ClueRequest;
import com.hideandseek.store.GameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClueMaintenanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClueMaintenanceService.class);
    
    @Autowired
    private GameStore gameStore;
    
    // Run every minute to check for expired clue requests
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredClueRequests() {
        try {
            logger.debug("Checking for expired clue requests...");
            
            List<ClueRequest> expiredRequests = gameStore.getExpiredClueRequests();
            for (ClueRequest request : expiredRequests) {
                request.setStatus("expired");
                gameStore.updateClueRequest(request);
                logger.info("Marked clue request {} as expired", request.getId());
            }
            
            if (!expiredRequests.isEmpty()) {
                logger.info("Cleaned up {} expired clue requests", expiredRequests.size());
            }
            
        } catch (Exception e) {
            logger.error("Error during clue request cleanup", e);
        }
    }
}
