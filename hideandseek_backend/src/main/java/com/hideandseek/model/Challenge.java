package com.hideandseek.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Challenge {
    private String id;                // Add id field
    private String title;
    private String description;
    @JsonProperty("token_reward")
    private int tokenReward;          // Use int instead of Object for consistency
    private Curse curse;              // Add curse field for penalties
    
    // Default constructor
    public Challenge() {}
    
    // Constructor with all fields
    public Challenge(String id, String title, String description, int tokenReward, Curse curse) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.tokenReward = tokenReward;
        this.curse = curse;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
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
    
    public int getTokenReward() {
        return tokenReward;
    }
    
    public void setTokenReward(int tokenReward) {
        this.tokenReward = tokenReward;
    }
    
    public Curse getCurse() {
        return curse;
    }
    
    public void setCurse(Curse curse) {
        this.curse = curse;
    }
}
