package com.hideandseek.model;

import java.util.List;
import java.util.ArrayList;

public class PurchasedClue {
    private String id;
    private String clueTypeId;
    private String teamId;
    private String gameId;
    private String clueText;
    private int cost;
    private long timestamp;
    private String status; // "completed", "pending", "expired"
    private String requestId; // For async clues, links to ClueRequest
    private String responseType; // "text", "photo", "location", "automatic"
    private String targetHiderTeamId; // For clues targeting specific hider teams (backward compatibility)
    
    // Location data for exact location clues (backward compatibility)
    private Double latitude;
    private Double longitude;
    private String targetTeamName;
    
    // New fields for multi-hider clues
    private List<String> targetHiderTeamIds; // Multiple targeted hider teams
    private List<HiderClueData> hiderData; // Aggregated data for multiple hiders
    
    // Inner class to hold data for each hider in the clue
    public static class HiderClueData {
        private String teamId;
        private String teamName;
        private Double latitude;
        private Double longitude;
        private Double distance; // Distance from seeker
        private String direction; // Cardinal direction from seeker
        private String additionalData; // For selfie URLs, landmark names, etc.
        
        public HiderClueData() {}
        
        public HiderClueData(String teamId, String teamName) {
            this.teamId = teamId;
            this.teamName = teamName;
        }
        
        // Getters and setters
        public String getTeamId() { return teamId; }
        public void setTeamId(String teamId) { this.teamId = teamId; }
        
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public Double getDistance() { return distance; }
        public void setDistance(Double distance) { this.distance = distance; }
        
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        
        public String getAdditionalData() { return additionalData; }
        public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }
    }

    // Default constructor
    public PurchasedClue() {
        this.timestamp = System.currentTimeMillis();
        this.status = "completed";
    }

    // Constructor with all fields (backward compatibility)
    public PurchasedClue(String id, String clueTypeId, String teamId, String gameId, String clueText, int cost) {
        this.id = id;
        this.clueTypeId = clueTypeId;
        this.teamId = teamId;
        this.gameId = gameId;
        this.clueText = clueText;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.status = "completed";
        this.responseType = "automatic";
    }
    
    // Constructor for async clues
    public PurchasedClue(String id, String clueTypeId, String teamId, String gameId, String clueText, 
                        int cost, String status, String requestId, String responseType, String targetHiderTeamId) {
        this.id = id;
        this.clueTypeId = clueTypeId;
        this.teamId = teamId;
        this.gameId = gameId;
        this.clueText = clueText;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.requestId = requestId;
        this.responseType = responseType;
        this.targetHiderTeamId = targetHiderTeamId;
    }
    
    // Constructor for location clues with coordinate data
    public PurchasedClue(String id, String clueTypeId, String teamId, String gameId, String clueText, 
                        int cost, String status, String requestId, String responseType, String targetHiderTeamId,
                        Double latitude, Double longitude, String targetTeamName) {
        this.id = id;
        this.clueTypeId = clueTypeId;
        this.teamId = teamId;
        this.gameId = gameId;
        this.clueText = clueText;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.requestId = requestId;
        this.responseType = responseType;
        this.targetHiderTeamId = targetHiderTeamId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.targetTeamName = targetTeamName;
    }
    
    // Constructor for multi-hider clues
    public PurchasedClue(String id, String clueTypeId, String teamId, String gameId, String clueText, 
                        int cost, String status, String requestId, String responseType, 
                        List<String> targetHiderTeamIds, List<HiderClueData> hiderData) {
        this.id = id;
        this.clueTypeId = clueTypeId;
        this.teamId = teamId;
        this.gameId = gameId;
        this.clueText = clueText;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
        this.status = status;
        this.requestId = requestId;
        this.responseType = responseType;
        this.targetHiderTeamIds = targetHiderTeamIds;
        this.hiderData = hiderData;
        
        // For backward compatibility, set single target if only one hider
        if (targetHiderTeamIds != null && !targetHiderTeamIds.isEmpty()) {
            this.targetHiderTeamId = targetHiderTeamIds.get(0);
        }
        if (hiderData != null && !hiderData.isEmpty()) {
            HiderClueData firstHider = hiderData.get(0);
            this.latitude = firstHider.getLatitude();
            this.longitude = firstHider.getLongitude();
            this.targetTeamName = firstHider.getTeamName();
        }
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClueTypeId() {
        return clueTypeId;
    }

    public void setClueTypeId(String clueTypeId) {
        this.clueTypeId = clueTypeId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getClueText() {
        return clueText;
    }

    public void setClueText(String clueText) {
        this.clueText = clueText;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getResponseType() {
        return responseType;
    }
    
    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }
    
    public String getTargetHiderTeamId() {
        return targetHiderTeamId;
    }
    
    public void setTargetHiderTeamId(String targetHiderTeamId) {
        this.targetHiderTeamId = targetHiderTeamId;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public String getTargetTeamName() {
        return targetTeamName;
    }
    
    public void setTargetTeamName(String targetTeamName) {
        this.targetTeamName = targetTeamName;
    }
    
    public List<String> getTargetHiderTeamIds() {
        return targetHiderTeamIds;
    }
    
    public void setTargetHiderTeamIds(List<String> targetHiderTeamIds) {
        this.targetHiderTeamIds = targetHiderTeamIds;
    }
    
    public List<HiderClueData> getHiderData() {
        return hiderData;
    }
    
    public void setHiderData(List<HiderClueData> hiderData) {
        this.hiderData = hiderData;
    }
}
