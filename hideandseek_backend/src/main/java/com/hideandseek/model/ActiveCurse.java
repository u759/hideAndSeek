package com.hideandseek.model;

public class ActiveCurse {
    private Curse curse;
    private long startTime;     // Timestamp in milliseconds
    private long endTime;       // Timestamp in milliseconds (for frontend compatibility)
    private boolean completed;  // Whether the hider marked this curse as completed
    private Long completedAt;   // When it was marked completed (ms)
    private boolean acknowledged; // Whether the hider acknowledged seeing the curse
    
    // Default constructor
    public ActiveCurse() {}
    
    // Constructor with all fields
    public ActiveCurse(Curse curse, long startTime, long endTime) {
        this.curse = curse;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    // Getters and Setters
    public Curse getCurse() {
        return curse;
    }
    
    public void setCurse(Curse curse) {
        this.curse = curse;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public void setDuration(int durationMinutes) {
        this.endTime = this.startTime + (durationMinutes * 60 * 1000);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }
}
