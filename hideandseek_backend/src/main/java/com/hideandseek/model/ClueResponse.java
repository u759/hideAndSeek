package com.hideandseek.model;

public class ClueResponse {
    private String requestId;
    private String gameId;
    private String respondingTeamId;
    private String requestingTeamId;
    private String clueTypeId;
    private String responseType; // "text", "photo", "location", "automatic"
    private String responseData; // The actual response content
    private long responseTimestamp;
    private boolean delivered; // Whether the response has been delivered to requesting team
    
    // Default constructor
    public ClueResponse() {
        this.responseTimestamp = System.currentTimeMillis();
        this.delivered = false;
    }
    
    // Constructor with essential fields
    public ClueResponse(String requestId, String gameId, String respondingTeamId, 
                       String requestingTeamId, String clueTypeId, String responseType, String responseData) {
        this.requestId = requestId;
        this.gameId = gameId;
        this.respondingTeamId = respondingTeamId;
        this.requestingTeamId = requestingTeamId;
        this.clueTypeId = clueTypeId;
        this.responseType = responseType;
        this.responseData = responseData;
        this.responseTimestamp = System.currentTimeMillis();
        this.delivered = false;
    }
    
    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public String getRespondingTeamId() {
        return respondingTeamId;
    }
    
    public void setRespondingTeamId(String respondingTeamId) {
        this.respondingTeamId = respondingTeamId;
    }
    
    public String getRequestingTeamId() {
        return requestingTeamId;
    }
    
    public void setRequestingTeamId(String requestingTeamId) {
        this.requestingTeamId = requestingTeamId;
    }
    
    public String getClueTypeId() {
        return clueTypeId;
    }
    
    public void setClueTypeId(String clueTypeId) {
        this.clueTypeId = clueTypeId;
    }
    
    public String getResponseType() {
        return responseType;
    }
    
    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }
    
    public String getResponseData() {
        return responseData;
    }
    
    public void setResponseData(String responseData) {
        this.responseData = responseData;
    }
    
    public long getResponseTimestamp() {
        return responseTimestamp;
    }
    
    public void setResponseTimestamp(long responseTimestamp) {
        this.responseTimestamp = responseTimestamp;
    }
    
    public boolean isDelivered() {
        return delivered;
    }
    
    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }
}
