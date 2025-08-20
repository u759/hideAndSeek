package com.hideandseek.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Game {
    private String id;
    private String code;              // 6-letter game code
    private List<Team> teams;
    private Long startTime;           // Timestamp in milliseconds for frontend compatibility
    private Long pauseTime;           // Timestamp when game was paused (null if not paused)
    private Long totalPausedDuration; // Total time spent paused in milliseconds
    private Long endTime;             // Timestamp when game ended
    @JsonProperty("round")
    private Integer round;            // Use "round" to match frontend expectation
    private String status;            // String status to match frontend
    private Integer roundLengthMinutes; // Round length in minutes (null = no time limit)
    private Boolean pausedByTimeLimit; // True if paused due to round time limit
    private Long lastActivityTime;    // Timestamp of last activity for cleanup purposes

    // Default constructor
    public Game() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    // Constructor with all fields
    public Game(String id, String code, List<Team> teams, Long startTime, Integer round, String status) {
        this.id = id;
        this.code = code;
        this.teams = teams;
        this.startTime = startTime;
        this.pauseTime = null;
        this.totalPausedDuration = 0L;
        this.round = round;
        this.status = status;
        this.roundLengthMinutes = null;
        this.pausedByTimeLimit = false;
        this.lastActivityTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public void setTeams(List<Team> teams) {
        this.teams = teams;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getPauseTime() {
        return pauseTime;
    }

    public void setPauseTime(Long pauseTime) {
        this.pauseTime = pauseTime;
    }

    public Long getTotalPausedDuration() {
        return totalPausedDuration;
    }

    public void setTotalPausedDuration(Long totalPausedDuration) {
        this.totalPausedDuration = totalPausedDuration;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Integer getRoundLengthMinutes() {
        return roundLengthMinutes;
    }

    public void setRoundLengthMinutes(Integer roundLengthMinutes) {
        this.roundLengthMinutes = roundLengthMinutes;
    }

    public Boolean getPausedByTimeLimit() {
        return pausedByTimeLimit;
    }

    public void setPausedByTimeLimit(Boolean pausedByTimeLimit) {
        this.pausedByTimeLimit = pausedByTimeLimit;
    }

    public Long getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(Long lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
}
