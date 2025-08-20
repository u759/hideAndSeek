package com.hideandseek.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RoundTimerService {

    @Autowired
    private GameService gameService;

    // Run every 5 seconds to proactively enforce round time limits and broadcast updates
    @Scheduled(fixedRate = 5000)
    public void pollAndEnforce() {
        try {
            gameService.enforceRoundTimeLimits();
        } catch (Exception e) {
            // Avoid noisy failures; GameService will log specifics
        }
    }
}
