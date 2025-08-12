package com.hideandseek.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Curse {
    private String title;
    private String description;
    @JsonProperty("token_count")
    private int tokenCount; // Use int for consistency
    
    // Default constructor
    public Curse() {}
    
    // Constructor with all fields
    public Curse(String title, String description, int tokenCount) {
        this.title = title;
        this.description = description;
        this.tokenCount = tokenCount;
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
}
