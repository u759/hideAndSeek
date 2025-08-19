package com.hideandseek.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AppliedCurse {
    private Curse curse;
    @JsonProperty("targetTeamId")
    private String targetTeamId;    // ID of the team this curse was applied to
    @JsonProperty("targetTeamName")
    private String targetTeamName;  // Name of the team for display purposes
    @JsonProperty("startTime")
    private long startTime;         // Timestamp in milliseconds
    @JsonProperty("endTime")
    private long endTime;           // Timestamp in milliseconds
    
    // Default constructor
    public AppliedCurse() {}
    
    // Constructor with all fields
    public AppliedCurse(Curse curse, String targetTeamId, String targetTeamName, long startTime, long endTime) {
        this.curse = curse;
        this.targetTeamId = targetTeamId;
        this.targetTeamName = targetTeamName;
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
    
    public String getTargetTeamId() {
        return targetTeamId;
    }
    
    public void setTargetTeamId(String targetTeamId) {
        this.targetTeamId = targetTeamId;
    }
    
    public String getTargetTeamName() {
        return targetTeamName;
    }
    
    public void setTargetTeamName(String targetTeamName) {
        this.targetTeamName = targetTeamName;
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
}
