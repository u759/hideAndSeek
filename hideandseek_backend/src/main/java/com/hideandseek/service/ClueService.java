package com.hideandseek.service;

import com.hideandseek.model.*;
import com.hideandseek.store.GameStore;
import com.hideandseek.websocket.GameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClueService {

    private static final Logger logger = LoggerFactory.getLogger(ClueService.class);

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
                    map.put("range", clueType.getRange()); // Include range field
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
            
            // Include multi-hider data if available
            if (clue.getTargetHiderTeamIds() != null) {
                map.put("targetHiderTeamIds", clue.getTargetHiderTeamIds());
            }
            if (clue.getHiderData() != null) {
                map.put("hiderData", clue.getHiderData());
            }
            
            // Include location data for exact location clues (legacy support)
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

        // Find hider teams within range instead of just the closest one
        List<Team> targetHiderTeams = gameStore.getHidersWithinRange(gameId, teamId, clueType.getRange());
        if (targetHiderTeams.isEmpty()) {
            throw new IllegalStateException("No hider teams found within range. No tokens deducted.");
        }

        // Process clue based on the new clue types from JSON
        return processClueByType(game, team, targetHiderTeams, clueType);
    }

    private Map<String, Object> processClueByType(Game game, Team requestingTeam, List<Team> targetHiderTeams, ClueType clueType) {
        String clueId = UUID.randomUUID().toString();
        
        switch (clueType.getId()) {
            case "exact-location":
                return handleExactLocationClue(game, requestingTeam, targetHiderTeams, clueType, clueId);
                
            case "selfie":
                return handleSelfieClue(game, requestingTeam, targetHiderTeams, clueType, clueId);
                
            case "closest-landmark":
                return handleClosestLandmarkClue(game, requestingTeam, targetHiderTeams, clueType, clueId);
                
            case "relative-direction":
                return handleRelativeDirectionClue(game, requestingTeam, targetHiderTeams, clueType, clueId);
                
            case "distance-from-seekers":
                return handleDistanceFromSeekersClue(game, requestingTeam, targetHiderTeams, clueType, clueId);
                
            default:
                throw new IllegalArgumentException("Unknown clue type: " + clueType.getId());
        }
    }

    private Map<String, Object> handleExactLocationClue(Game game, Team requestingTeam, List<Team> targetHiderTeams, ClueType clueType, String clueId) {
        // Create aggregated clue data for all hiders in range
        List<PurchasedClue.HiderClueData> hiderDataList = new ArrayList<>();
        List<String> targetTeamIds = new ArrayList<>();
        StringBuilder clueTextBuilder = new StringBuilder();
        
        if (targetHiderTeams.size() == 1) {
            Team hider = targetHiderTeams.get(0);
            clueTextBuilder.append(String.format("Exact location of %s: %.6f, %.6f", 
                    hider.getName(),
                    hider.getLocation().getLatitude(),
                    hider.getLocation().getLongitude()));
        } else {
            clueTextBuilder.append("Exact locations of hiders within range:\n");
            for (int i = 0; i < targetHiderTeams.size(); i++) {
                Team hider = targetHiderTeams.get(i);
                clueTextBuilder.append(String.format("%d. %s: %.6f, %.6f", 
                        i + 1,
                        hider.getName(),
                        hider.getLocation().getLatitude(),
                        hider.getLocation().getLongitude()));
                if (i < targetHiderTeams.size() - 1) {
                    clueTextBuilder.append("\n");
                }
            }
        }
        
        for (Team hider : targetHiderTeams) {
            PurchasedClue.HiderClueData hiderData = new PurchasedClue.HiderClueData(hider.getId(), hider.getName());
            hiderData.setLatitude(hider.getLocation().getLatitude());
            hiderData.setLongitude(hider.getLocation().getLongitude());
            
            // Calculate distance from seeker
            double distance = calculateDistance(requestingTeam.getLocation(), hider.getLocation());
            hiderData.setDistance(distance);
            
            hiderDataList.add(hiderData);
            targetTeamIds.add(hider.getId());
        }
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueTextBuilder.toString(), clueType.getCost(), "completed", null, "location", 
                targetTeamIds, hiderDataList
        );
        
        // Deduct tokens and save clue
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        // Push notify all targeted hider teams
        for (Team hider : targetHiderTeams) {
            pushService.notifyClueRequested(game.getId(), hider.getId(), clueType.getId(), clueType.getName());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "completed");
        
        // Create locations array for multiple hiders
        List<Map<String, Object>> locations = new ArrayList<>();
        for (PurchasedClue.HiderClueData hiderData : hiderDataList) {
            locations.add(Map.of(
                    "latitude", hiderData.getLatitude(),
                    "longitude", hiderData.getLongitude(),
                    "teamName", hiderData.getTeamName(),
                    "teamId", hiderData.getTeamId(),
                    "distance", hiderData.getDistance()
            ));
        }
        result.put("locations", locations);
        
        return result;
    }

    private Map<String, Object> handleSelfieClue(Game game, Team requestingTeam, List<Team> hiderTeams, ClueType clueType, String clueId) {
        // For multiple hiders, create separate selfie requests for each and prepare for aggregation
        List<String> requestIds = new ArrayList<>();
        List<PurchasedClue.HiderClueData> hiderDataList = new ArrayList<>();
        
        for (Team hider : hiderTeams) {
            String requestId = UUID.randomUUID().toString();
            requestIds.add(requestId);
            
            ClueRequest clueRequest = new ClueRequest(
                    requestId, game.getId(), requestingTeam.getId(), hider.getId(),
                    clueType.getId(), clueType.getName(), "photo"
            );
            gameStore.addClueRequest(clueRequest);
            
            // Create hider data entry for aggregation (selfie URLs will be added when responses come in)
            PurchasedClue.HiderClueData hiderData = new PurchasedClue.HiderClueData(hider.getId(), hider.getName());
            hiderData.setAdditionalData("Pending selfie from " + hider.getName());
            hiderDataList.add(hiderData);
            
            // Broadcast clue request to this specific hider team
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("id", requestId);
            requestData.put("clueTypeId", clueType.getId());
            requestData.put("clueTypeName", clueType.getName());
            requestData.put("requestingTeamName", "Seeker Team");
            requestData.put("responseType", "photo");
            requestData.put("description", "Take a selfie of your whole team at arm's length, including your surroundings.\n\n\"Surroundings\" is defined as:\n• The exterior of your nearest building, including its roof,\n• OR the interior of the building you are in,\n• OR if neither are possible, then your photo must capture your general surroundings and can be definitively photo-matched to the IRL spot it was taken at, with no room for doubt.");
            requestData.put("expirationTimestamp", clueRequest.getExpirationTimestamp());
            
            webSocketHandler.broadcastClueRequest(game.getId(), hider.getId(), requestData);
            pushService.notifyClueRequested(game.getId(), hider.getId(), clueType.getId(), clueType.getName());
        }
        
        // Create aggregated pending clue
        String clueText;
        if (hiderTeams.size() == 1) {
            clueText = "Waiting for selfie from " + hiderTeams.get(0).getName() + "...";
        } else {
            clueText = String.format("Waiting for selfies from %d hider teams. You'll be able to swipe through all received selfies.", hiderTeams.size());
        }
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "pending", 
                String.join(",", requestIds), // Store all request IDs
                "photo", 
                hiderTeams.stream().map(Team::getId).collect(Collectors.toList()),
                hiderDataList
        );
        
        // Deduct tokens and save
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Also broadcast general game update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "pending");
        result.put("requestIds", requestIds); // Changed to requestIds array
        return result;
    }

    private Map<String, Object> handleClosestLandmarkClue(Game game, Team requestingTeam, List<Team> hiderTeams, ClueType clueType, String clueId) {
        // For multiple hiders, create separate requests for each and aggregate responses
        List<String> requestIds = new ArrayList<>();
        List<PurchasedClue.HiderClueData> hiderDataList = new ArrayList<>();
        
        for (Team hider : hiderTeams) {
            String requestId = UUID.randomUUID().toString();
            requestIds.add(requestId);
            
            ClueRequest clueRequest = new ClueRequest(
                    requestId, game.getId(), requestingTeam.getId(), hider.getId(),
                    clueType.getId(), clueType.getName(), "text"
            );
            gameStore.addClueRequest(clueRequest);
            
            // Create hider data entry for aggregation
            PurchasedClue.HiderClueData hiderData = new PurchasedClue.HiderClueData(hider.getId(), hider.getName());
            hiderData.setAdditionalData("Pending landmark response from " + hider.getName());
            hiderDataList.add(hiderData);
            
            // Broadcast clue request to this specific hider team
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("id", requestId);
            requestData.put("clueTypeId", clueType.getId());
            requestData.put("clueTypeName", clueType.getName());
            requestData.put("requestingTeamName", "Seeker Team");
            requestData.put("responseType", "text");
            requestData.put("description", "Choose from: 1. Named street, 2. Library, 3. Museum, 4. Parking lot. Tell the seekers the name of the closest landmark of your chosen type.");
            requestData.put("expirationTimestamp", clueRequest.getExpirationTimestamp());
            
            webSocketHandler.broadcastClueRequest(game.getId(), hider.getId(), requestData);
            pushService.notifyClueRequested(game.getId(), hider.getId(), clueType.getId(), clueType.getName());
        }
        
        // Create aggregated pending clue
        String clueText;
        if (hiderTeams.size() == 1) {
            clueText = "Waiting for landmark information from " + hiderTeams.get(0).getName() + "...";
        } else {
            clueText = String.format("Waiting for landmark information from %d hider teams...", hiderTeams.size());
        }
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "pending", 
                String.join(",", requestIds), // Store all request IDs
                "text", 
                hiderTeams.stream().map(Team::getId).collect(Collectors.toList()),
                hiderDataList
        );
        
        // Deduct tokens and save
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Also broadcast general game update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "pending");
        result.put("requestIds", requestIds); // Changed to requestIds array
        return result;
    }

    private Map<String, Object> handleRelativeDirectionClue(Game game, Team requestingTeam, List<Team> hiderTeams, ClueType clueType, String clueId) {
        // Create aggregated direction clue for all hiders within range
        List<PurchasedClue.HiderClueData> hiderDataList = new ArrayList<>();
        
        for (Team hider : hiderTeams) {
            String direction = getDirection(requestingTeam.getLocation(), hider.getLocation());
            double distance = calculateDistance(requestingTeam.getLocation(), hider.getLocation());
            
            PurchasedClue.HiderClueData hiderData = new PurchasedClue.HiderClueData(hider.getId(), hider.getName());
            hiderData.setDirection(direction);
            hiderData.setDistance(distance);
            hiderData.setAdditionalData(String.format("Team %s is generally to the %s (%.0fm away)", 
                    hider.getName(), direction, distance));
            
            hiderDataList.add(hiderData);
        }
        
        // Create aggregated clue text
        String clueText;
        if (hiderTeams.size() == 1) {
            clueText = String.format("The hider (%s) is generally to the %s of your current position.", 
                    hiderTeams.get(0).getName(), getDirection(requestingTeam.getLocation(), hiderTeams.get(0).getLocation()));
        } else {
            // Include all directions in the main text
            StringBuilder directions = new StringBuilder();
            directions.append(String.format("Found %d hiders within range:\n", hiderTeams.size()));
            for (Team hider : hiderTeams) {
                String direction = getDirection(requestingTeam.getLocation(), hider.getLocation());
                double distance = calculateDistance(requestingTeam.getLocation(), hider.getLocation());
                directions.append(String.format("• %s: %s (%.0fm away)\n", hider.getName(), direction, distance));
            }
            clueText = directions.toString().trim();
        }
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "completed", null, "automatic", 
                hiderTeams.stream().map(Team::getId).collect(Collectors.toList()),
                hiderDataList
        );
        
        // Deduct tokens and save clue
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", purchasedClue.getId());
        result.put("text", purchasedClue.getClueText());
        result.put("cost", purchasedClue.getCost());
        result.put("timestamp", purchasedClue.getTimestamp());
        result.put("status", "completed");
        return result;
    }

    private Map<String, Object> handleDistanceFromSeekersClue(Game game, Team requestingTeam, List<Team> hiderTeams, ClueType clueType, String clueId) {
        // Create aggregated distance clue for all hiders within range
        List<PurchasedClue.HiderClueData> hiderDataList = new ArrayList<>();
        
        for (Team hider : hiderTeams) {
            double distance = calculateDistance(requestingTeam.getLocation(), hider.getLocation());
            
            PurchasedClue.HiderClueData hiderData = new PurchasedClue.HiderClueData(hider.getId(), hider.getName());
            hiderData.setDistance(distance);
            hiderData.setAdditionalData(String.format("Distance to %s: %.0f meters", hider.getName(), distance));
            
            hiderDataList.add(hiderData);
        }
        
        // Create aggregated clue text
        String clueText;
        if (hiderTeams.size() == 1) {
            double distance = calculateDistance(requestingTeam.getLocation(), hiderTeams.get(0).getLocation());
            clueText = String.format("Your distance to the hider (%s) is approximately %.0f meters.", 
                    hiderTeams.get(0).getName(), distance);
        } else {
            // Include all distances in the main text
            StringBuilder distances = new StringBuilder();
            distances.append(String.format("Found %d hiders within range:\n", hiderTeams.size()));
            for (Team hider : hiderTeams) {
                double distance = calculateDistance(requestingTeam.getLocation(), hider.getLocation());
                distances.append(String.format("• %s: %.0fm away\n", hider.getName(), distance));
            }
            clueText = distances.toString().trim();
        }
        
        var purchasedClue = new PurchasedClue(
                clueId, clueType.getId(), requestingTeam.getId(), game.getId(),
                clueText, clueType.getCost(), "completed", null, "automatic", 
                hiderTeams.stream().map(Team::getId).collect(Collectors.toList()),
                hiderDataList
        );
        
        // Deduct tokens and save clue
        requestingTeam.setTokens(requestingTeam.getTokens() - clueType.getCost());
        gameStore.updateGame(game);
        gameStore.addClueToHistory(game.getId(), purchasedClue);
        
        // Broadcast update
        webSocketHandler.broadcastToGame(game.getId(), game);
        
        // Push notify all targeted hider teams
        for (Team hider : hiderTeams) {
            pushService.notifyClueRequested(game.getId(), hider.getId(), clueType.getId(), clueType.getName());
        }
        
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
        
        // Handle multi-hider clue aggregation
        updateMultiHiderClueWithResponse(request, responseData);
        
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

    private void updateMultiHiderClueWithResponse(ClueRequest request, String responseData) {
        // Find the purchased clue that contains this request ID
        List<PurchasedClue> clues = gameStore.getClueHistoryForTeam(request.getGameId(), request.getRequestingTeamId());
        
        for (PurchasedClue clue : clues) {
            if (clue.getRequestId() != null && clue.getRequestId().contains(request.getId())) {
                // This is a multi-hider clue containing our request
                updateMultiHiderClueData(clue, request, responseData);
                gameStore.updateClueInHistory(request.getGameId(), clue);
                break;
            }
        }
    }
    
    private void updateMultiHiderClueData(PurchasedClue clue, ClueRequest request, String responseData) {
        if (clue.getHiderData() == null) {
            return;
        }
        
        // Find the hider data entry for this team and update it
        for (PurchasedClue.HiderClueData hiderData : clue.getHiderData()) {
            if (hiderData.getTeamId().equals(request.getTargetHiderTeamId())) {
                // Update the hider's data with the response
                if ("selfie".equals(request.getClueTypeId())) {
                    hiderData.setAdditionalData(responseData); // URL for selfie
                } else if ("closest-landmark".equals(request.getClueTypeId())) {
                    hiderData.setAdditionalData(responseData); // Landmark name
                }
                break;
            }
        }
        
        // Check if all responses are received and update clue status
        checkAndUpdateClueCompletion(clue, request.getGameId());
    }
    
    private void checkAndUpdateClueCompletion(PurchasedClue clue, String gameId) {
        if (clue.getRequestId() == null || clue.getHiderData() == null) {
            return;
        }
        
        String[] requestIds = clue.getRequestId().split(",");
        int completedCount = 0;
        int expiredCount = 0;
        
        // Count completed and expired requests
        for (String reqId : requestIds) {
            ClueRequest req = gameStore.getClueRequest(reqId.trim());
            if (req != null) {
                if ("completed".equals(req.getStatus())) {
                    completedCount++;
                } else if ("expired".equals(req.getStatus()) || req.isExpired()) {
                    expiredCount++;
                }
            }
        }
        
        // Update clue status if all requests are resolved
        if (completedCount + expiredCount >= requestIds.length) {
            clue.setStatus("completed");
            
            // Update clue text to show completion/timeout results
            updateClueTextForCompletion(clue, completedCount, expiredCount, requestIds.length);
        }
    }
    
    private void updateClueTextForCompletion(PurchasedClue clue, int completedCount, int expiredCount, int totalRequests) {
        if (clue.getHiderData().size() == 1) {
            // Single hider case
            PurchasedClue.HiderClueData hiderData = clue.getHiderData().get(0);
            if (hiderData.getLatitude() != null && hiderData.getLongitude() != null) {
                // Location available (either from response or timeout)
                clue.setClueText(String.format("Exact location of %s: %.6f, %.6f", 
                        hiderData.getTeamName(),
                        hiderData.getLatitude(),
                        hiderData.getLongitude()));
            } else if ("selfie".equals(clue.getClueTypeId())) {
                clue.setClueText("Selfie received from " + hiderData.getTeamName());
            } else if ("closest-landmark".equals(clue.getClueTypeId())) {
                clue.setClueText("Landmark information received from " + hiderData.getTeamName() + ": " + hiderData.getAdditionalData());
            }
        } else {
            // Multiple hiders case
            boolean hasAnyLocations = clue.getHiderData().stream()
                    .anyMatch(h -> h.getLatitude() != null && h.getLongitude() != null);
            
            if (hasAnyLocations) {
                // If any locations are revealed, format like an exact location clue
                StringBuilder sb = new StringBuilder();
                sb.append("Exact locations of hiders within range:\n");
                int locationCount = 1;
                for (PurchasedClue.HiderClueData hiderData : clue.getHiderData()) {
                    if (hiderData.getLatitude() != null && hiderData.getLongitude() != null) {
                        sb.append(String.format("%d. %s: %.6f, %.6f", 
                                locationCount++,
                                hiderData.getTeamName(),
                                hiderData.getLatitude(),
                                hiderData.getLongitude()));
                        if (hiderData.getAdditionalData() != null && hiderData.getAdditionalData().contains("timeout")) {
                            sb.append(" (timeout)");
                        }
                        sb.append("\n");
                    }
                }
                clue.setClueText(sb.toString().trim());
            } else {
                // Regular multi-hider response text
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Received responses from %d/%d hider teams:\n", completedCount, totalRequests));
                for (PurchasedClue.HiderClueData hiderData : clue.getHiderData()) {
                    if (hiderData.getAdditionalData() != null && !hiderData.getAdditionalData().startsWith("Pending")) {
                        if ("selfie".equals(clue.getClueTypeId())) {
                            sb.append(String.format("• %s: Selfie received\n", hiderData.getTeamName()));
                        } else {
                            sb.append(String.format("• %s: %s\n", hiderData.getTeamName(), hiderData.getAdditionalData()));
                        }
                    }
                }
                clue.setClueText(sb.toString().trim());
            }
        }
    }
    
    private void handleExpiredClueRequest(PurchasedClue clue, ClueRequest expiredRequest) {
        logger.info("Processing expired request: {} for clue: {} (type: {})", 
                   expiredRequest.getId(), clue.getId(), clue.getClueTypeId());
        
        // For expired requests, create a new separate exact location clue
        Game game = gameStore.getGame(expiredRequest.getGameId());
        if (game != null) {
            Team hiderTeam = game.getTeams().stream()
                    .filter(t -> t.getId().equals(expiredRequest.getTargetHiderTeamId()))
                    .findFirst()
                    .orElse(null);
            
            if (hiderTeam != null && hiderTeam.getLocation() != null) {
                logger.info("Creating separate exact location clue for timeout - hider team: {} at {}, {}", 
                           hiderTeam.getName(), 
                           hiderTeam.getLocation().getLatitude(), 
                           hiderTeam.getLocation().getLongitude());
                
                // Find the requesting team
                Team requestingTeam = game.getTeams().stream()
                        .filter(t -> t.getId().equals(clue.getTeamId()))
                        .findFirst()
                        .orElse(null);
                
                if (requestingTeam != null) {
                    // Create a new separate exact location clue
                    String timeoutClueId = UUID.randomUUID().toString();
                    double distance = calculateDistance(requestingTeam.getLocation(), hiderTeam.getLocation());
                    
                    // Create hider data for the timeout clue
                    List<PurchasedClue.HiderClueData> timeoutHiderData = new ArrayList<>();
                    PurchasedClue.HiderClueData timeoutData = new PurchasedClue.HiderClueData(hiderTeam.getId(), hiderTeam.getName());
                    timeoutData.setLatitude(hiderTeam.getLocation().getLatitude());
                    timeoutData.setLongitude(hiderTeam.getLocation().getLongitude());
                    timeoutData.setDistance(distance);
                    timeoutData.setAdditionalData("Auto-revealed due to no response");
                    timeoutHiderData.add(timeoutData);
                    
                    // Create the timeout clue text
                    String timeoutClueText = String.format("Exact location of %s (auto-revealed): %.6f, %.6f", 
                            hiderTeam.getName(),
                            hiderTeam.getLocation().getLatitude(),
                            hiderTeam.getLocation().getLongitude());
                    
                    // Create new exact location clue
                    PurchasedClue timeoutClue = new PurchasedClue(
                            timeoutClueId, "exact-location", requestingTeam.getId(), game.getId(),
                            timeoutClueText, 0, "completed", null, "location", 
                            List.of(hiderTeam.getId()), timeoutHiderData
                    );
                    
                    // Add the new clue to the game
                    gameStore.addClueToHistory(game.getId(), timeoutClue);
                    logger.info("Created separate timeout exact location clue: {}", timeoutClueId);
                    
                    // Update the original clue to remove this hider from pending status
                    updateOriginalClueForTimeout(clue, expiredRequest);
                    gameStore.updateClueInHistory(game.getId(), clue);
                    
                    // Broadcast timeout clue as a clue response to trigger frontend refresh
                    Map<String, Object> timeoutResponse = new HashMap<>();
                    timeoutResponse.put("type", "timeout");
                    timeoutResponse.put("clueId", timeoutClueId);
                    timeoutResponse.put("originalClueId", clue.getId());
                    timeoutResponse.put("hiderTeamId", hiderTeam.getId());
                    timeoutResponse.put("hiderTeamName", hiderTeam.getName());
                    timeoutResponse.put("latitude", hiderTeam.getLocation().getLatitude());
                    timeoutResponse.put("longitude", hiderTeam.getLocation().getLongitude());
                    timeoutResponse.put("message", "Auto-revealed location due to timeout");
                    
                    webSocketHandler.broadcastClueResponse(game.getId(), requestingTeam.getId(), timeoutResponse);
                    
                    // Also broadcast general game update to all players
                    webSocketHandler.broadcastToGame(game.getId(), game);
                    logger.info("Broadcasted timeout clue response and game update");
                }
            } else {
                logger.warn("Could not find hider team or location for expired request: {}", expiredRequest.getId());
            }
        }
        
        // Mark request as expired
        expiredRequest.setStatus("expired");
        gameStore.updateClueRequest(expiredRequest);
        logger.info("Marked request as expired: {}", expiredRequest.getId());
    }
    
    private void updateOriginalClueForTimeout(PurchasedClue originalClue, ClueRequest expiredRequest) {
        // Update the original clue to show that this hider timed out
        if (originalClue.getHiderData() != null) {
            for (PurchasedClue.HiderClueData hiderData : originalClue.getHiderData()) {
                if (hiderData.getTeamId().equals(expiredRequest.getTargetHiderTeamId())) {
                    hiderData.setAdditionalData("No response (see auto-revealed location clue)");
                    break;
                }
            }
        }
        
        // Check if this completes the original clue
        checkAndUpdateClueCompletion(originalClue, expiredRequest.getGameId());
    }

    // Debug method to check clue request status
    public Map<String, Object> getClueRequestsForDebug(String gameId) {
        Game game = gameStore.getGame(gameId);
        if (game == null) {
            return Map.of("error", "Game not found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("gameId", gameId);
        result.put("gameStatus", game.getStatus());
        
        // Get all clue requests for this game
        List<Map<String, Object>> allRequests = new ArrayList<>();
        List<ClueRequest> expiredRequests = gameStore.getExpiredClueRequests();
        
        // Filter requests by game ID (since GameStore doesn't have a direct method for this)
        for (Team team : game.getTeams()) {
            if ("seeker".equals(team.getRole())) {
                List<PurchasedClue> clueHistory = gameStore.getClueHistoryForTeam(gameId, team.getId());
                for (PurchasedClue clue : clueHistory) {
                    if (clue.getRequestId() != null) {
                        String[] requestIds = clue.getRequestId().split(",");
                        for (String reqId : requestIds) {
                            ClueRequest req = gameStore.getClueRequest(reqId.trim());
                            if (req != null) {
                                Map<String, Object> reqInfo = new HashMap<>();
                                reqInfo.put("id", req.getId());
                                reqInfo.put("status", req.getStatus());
                                reqInfo.put("clueType", req.getClueTypeId());
                                reqInfo.put("targetTeam", req.getTargetHiderTeamId());
                                reqInfo.put("responseType", req.getResponseType());
                                reqInfo.put("requestTime", req.getRequestTimestamp());
                                reqInfo.put("expirationTime", req.getExpirationTimestamp());
                                reqInfo.put("isExpired", req.isExpired());
                                reqInfo.put("timeLeft", req.getExpirationTimestamp() - System.currentTimeMillis());
                                allRequests.add(reqInfo);
                            }
                        }
                    }
                }
            }
        }
        
        result.put("allRequests", allRequests);
        result.put("expiredRequestCount", expiredRequests.stream()
                .filter(req -> req.getGameId().equals(gameId))
                .count());
        result.put("currentTime", System.currentTimeMillis());
        
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

    /**
     * Scheduled task that checks for expired clue requests every 30 seconds
     * and automatically reveals hider locations for landmark/selfie clues
     * that have not received responses within the timeout period.
     */
    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    public void handleExpiredClueRequests() {
        try {
            List<ClueRequest> expiredRequests = gameStore.getExpiredClueRequests();
            logger.info("Scheduled task running - found {} expired requests", expiredRequests.size());
            
            for (ClueRequest expiredRequest : expiredRequests) {
                logger.info("Handling expired clue request: {} for game: {}, type: {}, target: {}", 
                           expiredRequest.getId(), expiredRequest.getGameId(), 
                           expiredRequest.getClueTypeId(), expiredRequest.getTargetHiderTeamId());
                
                // Find the game and validate it's active
                Game game = gameStore.getGame(expiredRequest.getGameId());
                if (game == null || !"active".equals(game.getStatus())) {
                    logger.warn("Skipping expired request for inactive game: {}", expiredRequest.getGameId());
                    continue; // Skip if game not found or not active
                }
                
                // Find the clue that contains this request ID in its requestId field (comma-separated for multi-hider)
                PurchasedClue targetClue = null;
                for (Team team : game.getTeams()) {
                    if ("seeker".equals(team.getRole())) {
                        List<PurchasedClue> clueHistory = gameStore.getClueHistoryForTeam(expiredRequest.getGameId(), team.getId());
                        for (PurchasedClue clue : clueHistory) {
                            if (clue.getRequestId() != null && clue.getRequestId().contains(expiredRequest.getId())) {
                                targetClue = clue;
                                logger.info("Found target clue: {} for expired request: {}", clue.getId(), expiredRequest.getId());
                                break;
                            }
                        }
                    }
                    if (targetClue != null) break;
                }
                
                if (targetClue != null) {
                    logger.info("Processing expired request timeout for clue: {}", targetClue.getId());
                    handleExpiredClueRequest(targetClue, expiredRequest);
                } else {
                    logger.warn("Could not find target clue for expired request: {}", expiredRequest.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Error in scheduled task for handling expired clue requests", e);
        }
    }
}
