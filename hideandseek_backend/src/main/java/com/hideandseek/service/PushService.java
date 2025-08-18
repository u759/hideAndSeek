package com.hideandseek.service;

import com.hideandseek.store.GameStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PushService {

    @Autowired
    private GameStore gameStore;

    public void registerToken(String gameId, String teamId, String token) {
        if (gameId == null || teamId == null || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing gameId, teamId, or token");
        }
        gameStore.registerPushToken(gameId, teamId, token);
    }

    public Set<String> getTokens(String gameId, String teamId) {
        return gameStore.getPushTokens(gameId, teamId);
    }
}
