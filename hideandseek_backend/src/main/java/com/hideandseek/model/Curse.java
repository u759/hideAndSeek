package com.hideandseek.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Curse {
    private String id;
    private String title;
    private String description;
    @JsonProperty("token_count")
    private int tokenCount; // Use int for consistency
    @JsonProperty("time_seconds")
    private Integer timeSeconds; // Duration in seconds (nullable)
    @JsonProperty("penalty")
    private Integer penalty; // Time penalty in seconds if curse is not completed (nullable)
    
    // Default constructor
    public Curse() {}

    // Constructor with all fields
    public Curse(String id, String title, String description, int tokenCount, Integer timeSeconds, Integer penalty) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.tokenCount = tokenCount;
        this.timeSeconds = timeSeconds;
        this.penalty = penalty;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
    // Getters and Setters
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public int getTokenCount() {
        return tokenCount;
    }
    
    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }
    
    public Integer getTimeSeconds() {
        return timeSeconds;
    }
    
    public void setTimeSeconds(Integer timeSeconds) {
        this.timeSeconds = timeSeconds;
    }
    
    public Integer getPenalty() {
        return penalty;
    }
    
    public void setPenalty(Integer penalty) {
        this.penalty = penalty;
    }
}
