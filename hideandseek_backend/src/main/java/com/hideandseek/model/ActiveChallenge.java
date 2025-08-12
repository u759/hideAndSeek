package com.hideandseek.model;

public class ActiveChallenge {
    private Challenge challenge;
    private long startTime;     // Timestamp in milliseconds (for frontend compatibility)
    private boolean completed;
    
    // Default constructor
    public ActiveChallenge() {}
    
    // Constructor with all fields
    public ActiveChallenge(Challenge challenge, long startTime, boolean completed) {
        this.challenge = challenge;
        this.startTime = startTime;
        this.completed = completed;
    }
    
    // Getters and Setters
    public Challenge getChallenge() {
        return challenge;
    }
    
    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
