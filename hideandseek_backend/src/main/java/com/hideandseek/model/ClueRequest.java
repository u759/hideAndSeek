package com.hideandseek.model;

public class ClueRequest {
    private String id;
    private String gameId;
    private String requestingTeamId;
    private String targetHiderTeamId;
    private String clueTypeId;
    private String clueTypeName;
    private String status; // "pending", "completed", "expired"
    private long requestTimestamp;
    private long expirationTimestamp;
    private String response; // For text responses or photo URL
    private String responseType; // "text", "photo", "location", "automatic"
    
    // Default constructor
    public ClueRequest() {
        this.requestTimestamp = System.currentTimeMillis();
        this.status = "pending";
    }
    
    // Constructor with essential fields
    public ClueRequest(String id, String gameId, String requestingTeamId, String targetHiderTeamId, 
                      String clueTypeId, String clueTypeName, String responseType) {
        this.id = id;
        this.gameId = gameId;
        this.requestingTeamId = requestingTeamId;
        this.targetHiderTeamId = targetHiderTeamId;
        this.clueTypeId = clueTypeId;
        this.clueTypeName = clueTypeName;
        this.responseType = responseType;
        this.requestTimestamp = System.currentTimeMillis();
        this.status = "pending";
        // Set expiration to 5 minutes from now for manual responses
        if ("text".equals(responseType) || "photo".equals(responseType)) {
            this.expirationTimestamp = System.currentTimeMillis() + (5 * 60 * 1000);
        }
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public String getRequestingTeamId() {
        return requestingTeamId;
    }
    
    public void setRequestingTeamId(String requestingTeamId) {
        this.requestingTeamId = requestingTeamId;
    }
    
    public String getTargetHiderTeamId() {
        return targetHiderTeamId;
    }
    
    public void setTargetHiderTeamId(String targetHiderTeamId) {
        this.targetHiderTeamId = targetHiderTeamId;
    }
    
    public String getClueTypeId() {
        return clueTypeId;
    }
    
    public void setClueTypeId(String clueTypeId) {
        this.clueTypeId = clueTypeId;
    }
    
    public String getClueTypeName() {
        return clueTypeName;
    }
    
    public void setClueTypeName(String clueTypeName) {
        this.clueTypeName = clueTypeName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public long getRequestTimestamp() {
        return requestTimestamp;
    }
    
    public void setRequestTimestamp(long requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }
    
    public long getExpirationTimestamp() {
        return expirationTimestamp;
    }
    
    public void setExpirationTimestamp(long expirationTimestamp) {
        this.expirationTimestamp = expirationTimestamp;
    }
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public String getResponseType() {
        return responseType;
    }
    
    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }
    
    public boolean isExpired() {
        return expirationTimestamp > 0 && System.currentTimeMillis() > expirationTimestamp;
    }
}
