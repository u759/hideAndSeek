package com.hideandseek.service;

import com.hideandseek.model.Game;
import com.hideandseek.model.Team;
import com.hideandseek.model.Location;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameWebSocketHandler webSocketHandler;

    public void updateLocation(String gameId, String teamId, double latitude, double longitude) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        // Validate game is active before allowing location updates
        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot update location when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        Location location = new Location();
        location.setLatitude(latitude);
        location.setLongitude(longitude);

        team.setCurrentLocation(location);
        gameStore.updateGame(game);

        // Broadcast location update to WebSocket
        webSocketHandler.broadcastToGame(gameId, game);
    }
}
