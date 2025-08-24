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

    // New timing fields
    private Long gameStartTime;       // First time the game became active
    private Long roundStartTime;      // Start time of the current round
    private Long pausedDurationAtRoundStart; // Snapshot of totalPausedDuration when the round started

    // Default constructor
    public Game() {
        this.lastActivityTime = System.currentTimeMillis();
        this.totalPausedDuration = 0L;
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
        // Initialize new timing fields
        this.gameStartTime = startTime;
        this.roundStartTime = startTime;
        this.pausedDurationAtRoundStart = 0L;
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

    public Long getGameStartTime() {
        return gameStartTime;
    }

    public void setGameStartTime(Long gameStartTime) {
        this.gameStartTime = gameStartTime;
    }

    public Long getRoundStartTime() {
        return roundStartTime;
    }

    public void setRoundStartTime(Long roundStartTime) {
        this.roundStartTime = roundStartTime;
    }

    public Long getPausedDurationAtRoundStart() {
        return pausedDurationAtRoundStart;
    }

    public void setPausedDurationAtRoundStart(Long pausedDurationAtRoundStart) {
        this.pausedDurationAtRoundStart = pausedDurationAtRoundStart;
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

    // Computed properties for durations (exclude paused time, including current pause if any)
    @JsonProperty("gameDuration")
    public Long getGameDuration() {
        if (this.gameStartTime == null) return 0L;
        long nowOrEnd = ("ended".equals(this.status) && this.endTime != null) ? this.endTime : System.currentTimeMillis();
        long paused = this.totalPausedDuration != null ? this.totalPausedDuration : 0L;
        if ("paused".equals(this.status) && this.pauseTime != null) {
            paused += nowOrEnd - this.pauseTime;
        }
        long duration = nowOrEnd - this.gameStartTime - paused;
        return duration < 0 ? 0L : duration;
    }

    @JsonProperty("roundDuration")
    public Long getRoundDuration() {
        if (this.roundStartTime == null) return 0L;
        long nowOrEnd = ("ended".equals(this.status) && this.endTime != null) ? this.endTime : System.currentTimeMillis();
        long totalPaused = this.totalPausedDuration != null ? this.totalPausedDuration : 0L;
        long pausedBaseline = this.pausedDurationAtRoundStart != null ? this.pausedDurationAtRoundStart : 0L;
        long pausedSinceRoundStart = totalPaused - pausedBaseline;
        if (pausedSinceRoundStart < 0) pausedSinceRoundStart = 0L;
        if ("paused".equals(this.status) && this.pauseTime != null) {
            // Include in-progress paused segment
            pausedSinceRoundStart += nowOrEnd - this.pauseTime;
        }
        long duration = nowOrEnd - this.roundStartTime - pausedSinceRoundStart;
        return duration < 0 ? 0L : duration;
    }
}
