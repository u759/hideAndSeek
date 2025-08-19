package com.hideandseek.service;

import com.hideandseek.model.*;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClueService {

    // (no logger currently used)

    private final GameStore gameStore;
    private final GameWebSocketHandler webSocketHandler;
    private final PushService pushService;

    // No randomness currently used in this service

    public ClueService(GameStore gameStore, GameWebSocketHandler webSocketHandler, PushService pushService) {
        this.gameStore = gameStore;
        this.webSocketHandler = webSocketHandler;
        this.pushService = pushService;
    }

    public List<Map<String, Object>> getClueTypes() {
        return gameStore.getAllClueTypes().stream()
                .map(clueType -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", clueType.getId());
                    map.put("name", clueType.getName());
                    map.put("description", clueType.getDescription());
                    map.put("cost", clueType.getCost());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getClueHistory(String gameId, String teamId) {
        // Get purchased clues (both completed and pending)
        List<PurchasedClue> purchasedClues = gameStore.getClueHistoryForTeam(gameId, teamId);

        // Merge undelivered responses into the in-memory purchased clues before returning
        List<ClueResponse> responses = gameStore.getUndeliveredClueResponsesForTeam(gameId, teamId);
        if (responses != null && !responses.isEmpty()) {
            for (ClueResponse response : responses) {
                purchasedClues.stream()
                        .filter(clue -> clue.getRequestId() != null && clue.getRequestId().equals(response.getRequestId()))
                        .findFirst()
                        .ifPresent(clue -> {
                            clue.setClueText(response.getResponseData());
                            clue.setStatus("completed");
                        });

                // Mark response as delivered so it isn't re-applied
                gameStore.markClueResponseAsDelivered(response.getRequestId(), teamId, gameId);
            }
        }

        // Build response payload from the updated list
        List<Map<String, Object>> result = new ArrayList<>();
        for (PurchasedClue clue : purchasedClues) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", clue.getId());
            map.put("text", clue.getClueText());
            map.put("cost", clue.getCost());
            map.put("timestamp", clue.getTimestamp());
            map.put("status", clue.getStatus());
            map.put("clueTypeId", clue.getClueTypeId());
            map.put("responseType", clue.getResponseType());
            map.put("targetHiderTeamId", clue.getTargetHiderTeamId());
            
            // Include location data for exact location clues
            if (clue.getLatitude() != null && clue.getLongitude() != null) {
                Map<String, Object> location = new HashMap<>();
                location.put("latitude", clue.getLatitude());
                location.put("longitude", clue.getLongitude());
                location.put("teamName", clue.getTargetTeamName());
                map.put("location", location);
            }
            
            result.add(map);
        }

        return result;
    }

    public Map<String, Object> purchaseClue(String gameId, String teamId, String clueTypeId, String description) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        if (!"active".equals(game.getStatus())) {
            throw new IllegalStateException("Cannot purchase clues when game is not active");
        }

        Team team = gameStore.getTeam(gameId, teamId);
        if (team == null) {
            throw new IllegalArgumentException("Team not found");
        }

        // Check if team is a hider (hiders cannot purchase clues)
        if ("hider".equals(team.getRole())) {
            throw new IllegalStateException("Hiders cannot purchase clues. Only seekers can buy clues to find hiders.");
        }

        // Check if requesting team has location (required for distance-based clue targeting)
        // NOTE: Both seekers and hiders need location tracking enabled in the frontend
        if (team.getLocation() == null) {
            throw new IllegalStateException("Location required: wait for all players to acquire location (ensure your device has GPS enabled and has sent a location).");
        }

        var clueType = gameStore.getAllClueTypes().stream()
                .filter(ct -> ct.getId().equals(clueTypeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Clue type not found"));

        if (team.getTokens() < clueType.getCost()) {
            throw new IllegalStateException("Insufficient tokens");
        }

        // Find the closest hider team to apply the clue to
        Team targetHiderTeam = gameStore.getClosestHiderTeam(gameId, teamId);
        if (targetHiderTeam == null) {
            throw new IllegalStateException("Location required: wait for all players to acquire location (hider teams are still acquiring GPS).");
        }

        // Process clue based on the new clue types from JSON
        return processClueByType(game, team, targetHiderTeam, clueType);
    }

    private Map<String, Object> processClueByType(Game game, Team requestingTeam, Team targetHiderTeam, ClueType clueType) {
        String clueId = UUID.randomUUID().toString();
        
        switch (clueType.getId()) {
            case "exact-location":
                return handleExactLocationClue(game, requestingTeam, targetHiderTeam, clueType, clueId);
                
            case "selfie":
                return handleSelfieClue(game, requestingTeam, targetHiderTeam, clueType, clueId);
                
            case "closest-landmark":
                return handleClosestLandmarkClue(game, requestingTeam, targetHiderTeam, clueType, clueId);
                
            case "relative-direction":
                return handleRelativeDirectionClue(game, requestingTeam, targetHiderTeam, clueType, clueId);
                
            case "distance-from-seekers":
                return handleDistanceFromSeekersClue(game, requestingTeam, targetHiderTeam, clueType, clueId);
                
            default:
                throw new IllegalArgumentException("Unknown clue type: " + clueType.getId());
        }
    }

    private Map<String, Object> handleExactLocationClue(Game game, Team requestingTeam, Team targetHiderTeam, ClueType clueType, String clueId) {
        // Immediate response with exact location
        String clueText = String.format("Exact location of %s: %.6f, %.6f", 
                targetHiderTeam.getName(),
                targetHiderTeam.getLocation().getLatitude(),
                targetHiderTeam.getLocation().getLongitude());
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "completed", null, "location", targetHiderTeam.getId(),
                targetHiderTeam.getLocation().getLatitude(), targetHiderTeam.getLocation().getLongitude(),
                targetHiderTeam.getName()
        );
        
        // Deduct tokens and save clue
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast update
        webSocketHandler.broadcastToGame(game.getId(), game);
    // Push notify targeted hider team about the used clue (anonymous seeker)
    pushService.notifyClueRequested(game.getId(), targetHiderTeam.getId(), clueType.getId(), clueType.getName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "completed");
        result.put("location", Map.of(
                "latitude", targetHiderTeam.getLocation().getLatitude(),
                "longitude", targetHiderTeam.getLocation().getLongitude(),
                "teamName", targetHiderTeam.getName()
        ));
        return result;
    }

    private Map<String, Object> handleSelfieClue(Game game, Team requestingTeam, Team targetHiderTeam, ClueType clueType, String clueId) {
        // Create async clue request
        String requestId = UUID.randomUUID().toString();
        
        ClueRequest clueRequest = new ClueRequest(
                requestId, game.getId(), requestingTeam.getId(), targetHiderTeam.getId(),
                clueType.getId(), clueType.getName(), "photo"
        );
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                "Waiting for selfie from " + targetHiderTeam.getName() + "...", 
                clueType.getCost(), "pending", requestId, "photo", targetHiderTeam.getId()
        );
        
        // Deduct tokens and save
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueRequest(clueRequest);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast clue request specifically to target hider team
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("id", requestId);
        requestData.put("clueTypeId", clueType.getId());
        requestData.put("clueTypeName", clueType.getName());
        // Do NOT reveal the seeker in push/WS to hiders (anonymous)
        requestData.put("requestingTeamName", "Seeker Team");
        requestData.put("responseType", "photo");
        requestData.put("description", "Take a selfie of your whole team at arm's length, including your surroundings.");
        requestData.put("expirationTimestamp", clueRequest.getExpirationTimestamp());
        
    webSocketHandler.broadcastClueRequest(game.getId(), targetHiderTeam.getId(), requestData);
    // Push notify only the targeted hider team
    pushService.notifyClueRequested(game.getId(), targetHiderTeam.getId(), clueType.getId(), clueType.getName());
        
        // Also broadcast general game update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "pending");
        result.put("requestId", requestId);
        return result;
    }

    private Map<String, Object> handleClosestLandmarkClue(Game game, Team requestingTeam, Team targetHiderTeam, ClueType clueType, String clueId) {
        // Create async clue request for landmark name
        String requestId = UUID.randomUUID().toString();
        
        ClueRequest clueRequest = new ClueRequest(
                requestId, game.getId(), requestingTeam.getId(), targetHiderTeam.getId(),
                clueType.getId(), clueType.getName(), "text"
        );
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                "Waiting for landmark information from " + targetHiderTeam.getName() + "...", 
                clueType.getCost(), "pending", requestId, "text", targetHiderTeam.getId()
        );
        
        // Deduct tokens and save
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueRequest(clueRequest);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast clue request specifically to target hider team
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("id", requestId);
        requestData.put("clueTypeId", clueType.getId());
        requestData.put("clueTypeName", clueType.getName());
        // Do NOT reveal the seeker in push/WS to hiders (anonymous)
        requestData.put("requestingTeamName", "Seeker Team");
        requestData.put("responseType", "text");
        requestData.put("description", "Choose from: 1. Named street, 2. Library, 3. Museum, 4. Parking lot. Tell the seekers the name of the closest landmark of your chosen type.");
        requestData.put("expirationTimestamp", clueRequest.getExpirationTimestamp());
        
    webSocketHandler.broadcastClueRequest(game.getId(), targetHiderTeam.getId(), requestData);
    // Push notify only the targeted hider team
    pushService.notifyClueRequested(game.getId(), targetHiderTeam.getId(), clueType.getId(), clueType.getName());
        
        // Also broadcast general game update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "pending");
        result.put("requestId", requestId);
        return result;
    }

    private Map<String, Object> handleRelativeDirectionClue(Game game, Team requestingTeam, Team targetHiderTeam, ClueType clueType, String clueId) {
        // Immediate response with direction
        String direction = getDirection(requestingTeam.getLocation(), targetHiderTeam.getLocation());
        String clueText = String.format("The closest hider (%s) is generally to the %s of your current position.", 
                targetHiderTeam.getName(), direction);
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "completed", null, "automatic", targetHiderTeam.getId()
        );
        
        // Deduct tokens and save clue
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast update
        webSocketHandler.broadcastToGame(game.getId(), game);
    // Push notify targeted hider team about the used clue (anonymous seeker)
    pushService.notifyClueRequested(game.getId(), targetHiderTeam.getId(), clueType.getId(), clueType.getName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "completed");
        return result;
    }

    private Map<String, Object> handleDistanceFromSeekersClue(Game game, Team requestingTeam, Team targetHiderTeam, ClueType clueType, String clueId) {
        // Immediate response with distance
        double distance = calculateDistance(requestingTeam.getLocation(), targetHiderTeam.getLocation());
        String clueText = String.format("Your distance to the closest hider (%s) is approximately %.0f meters.", 
                targetHiderTeam.getName(), distance);
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "completed", null, "automatic", targetHiderTeam.getId()
        );
        
        // Deduct tokens and save clue
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast update
        webSocketHandler.broadcastToGame(game.getId(), game);
    // Push notify targeted hider team about the used clue (anonymous seeker)
    pushService.notifyClueRequested(game.getId(), targetHiderTeam.getId(), clueType.getId(), clueType.getName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "completed");
        return result;
    }

    // Method for hiders to get pending clue requests
    public List<Map<String, Object>> getPendingClueRequests(String gameId, String teamId) {
        List<ClueRequest> requests = gameStore.getPendingClueRequestsForTeam(gameId, teamId);
        return requests.stream()
                .map(request -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", request.getId());
                    map.put("clueTypeId", request.getClueTypeId());
                    map.put("clueTypeName", request.getClueTypeName());
                    map.put("requestingTeamName", gameStore.getTeam(gameId, request.getRequestingTeamId()).getName());
                    map.put("responseType", request.getResponseType());
                    map.put("requestTimestamp", request.getRequestTimestamp());
                    map.put("expirationTimestamp", request.getExpirationTimestamp());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // Method for hiders to respond to clue requests
    public Map<String, Object> respondToClueRequest(String requestId, String teamId, String responseData) {
        ClueRequest request = gameStore.getClueRequest(requestId);
        if (request == null) {
            throw new IllegalArgumentException("Clue request not found");
        }
        
        if (!request.getTargetHiderTeamId().equals(teamId)) {
            throw new IllegalArgumentException("This clue request is not for your team");
        }
        
        if (!"pending".equals(request.getStatus())) {
            throw new IllegalStateException("This clue request is no longer pending");
        }
        
        if (request.isExpired()) {
            request.setStatus("expired");
            gameStore.updateClueRequest(request);
            throw new IllegalStateException("This clue request has expired");
        }
        
        // Create response
        ClueResponse response = new ClueResponse(
                requestId, request.getGameId(), teamId, request.getRequestingTeamId(),
                request.getClueTypeId(), request.getResponseType(), responseData
        );
        
        // Update request status
        request.setStatus("completed");
        request.setResponse(responseData);
        gameStore.updateClueRequest(request);
        gameStore.addClueResponse(response);
        
        // Broadcast clue response to requesting team
        Map<String, Object> responseInfo = new HashMap<>();
        responseInfo.put("requestId", requestId);
        responseInfo.put("clueTypeId", request.getClueTypeId());
        responseInfo.put("responseType", request.getResponseType());
        responseInfo.put("responseData", responseData);
        responseInfo.put("timestamp", response.getResponseTimestamp());
        
        webSocketHandler.broadcastClueResponse(request.getGameId(), request.getRequestingTeamId(), responseInfo);
        
        // Broadcast general update to all players
        Game game = gameStore.getGame(request.getGameId());
        webSocketHandler.broadcastToGame(request.getGameId(), game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("status", "completed");
        result.put("responseData", responseData);
        result.put("timestamp", response.getResponseTimestamp());
        return result;
    }

    // Utility methods
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

    private String getDirection(Team.TeamLocation from, Team.TeamLocation to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lon1 = Math.toRadians(from.getLongitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double lon2 = Math.toRadians(to.getLongitude());

        double dlon = lon2 - lon1;
        double y = Math.sin(dlon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dlon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        bearing = (bearing + 360) % 360;

        if (bearing < 22.5 || bearing >= 337.5) return "north";
        else if (bearing < 67.5) return "northeast";
        else if (bearing < 112.5) return "east";
        else if (bearing < 157.5) return "southeast";
        else if (bearing < 202.5) return "south";
        else if (bearing < 247.5) return "southwest";
        else if (bearing < 292.5) return "west";
        else return "northwest";
    }
}
