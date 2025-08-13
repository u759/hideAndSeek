package com.hideandseek.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

public class Team {
    private String id;
    private String name;
    private String role;              // "seeker" or "hider" to match frontend
    private int tokens;
    @JsonProperty("location")
    private TeamLocation location;    // Nested location object to match frontend structure
    private List<String> completedChallenges = new ArrayList<>();
    private List<String> completedCurses = new ArrayList<>();
    private ActiveChallenge activeChallenge;  // Current challenge
    private List<ActiveCurse> activeCurses = new ArrayList<>();
    @JsonProperty("vetoEndTime")
    private Long vetoEndTime;         // Timestamp when veto penalty ends
    @JsonProperty("hiderStartTime")
    private Long hiderStartTime;      // When this team became a hider (null if not hider)
    @JsonProperty("totalHiderTime")
    private long totalHiderTime = 0;  // Total milliseconds spent as hider across all rounds
    
    // Default constructor
    public Team() {}
    
    // Constructor with all fields
    public Team(String id, String name, String role, int tokens, TeamLocation location, 
                List<String> completedChallenges, List<String> completedCurses, ActiveChallenge activeChallenge, 
                List<ActiveCurse> activeCurses, Long vetoEndTime, Long hiderStartTime, long totalHiderTime) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.tokens = tokens;
        this.location = location;
        this.completedChallenges = completedChallenges != null ? completedChallenges : new ArrayList<>();
        this.completedCurses = completedCurses != null ? completedCurses : new ArrayList<>();
        this.activeChallenge = activeChallenge;
        this.activeCurses = activeCurses != null ? activeCurses : new ArrayList<>();
        this.vetoEndTime = vetoEndTime;
        this.hiderStartTime = hiderStartTime;
        this.totalHiderTime = totalHiderTime;
    }    // Getters and Setters
    public List<String> getCompletedCurses() {
        return completedCurses;
    }

    public void setCompletedCurses(List<String> completedCurses) {
        this.completedCurses = completedCurses;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public TeamLocation getLocation() {
        return location;
    }

    public void setLocation(TeamLocation location) {
        this.location = location;
    }

    public List<String> getCompletedChallenges() {
        return completedChallenges;
    }

    public void setCompletedChallenges(List<String> completedChallenges) {
        this.completedChallenges = completedChallenges != null ? completedChallenges : new ArrayList<>();
    }

    public ActiveChallenge getActiveChallenge() {
        return activeChallenge;
    }

    public void setActiveChallenge(ActiveChallenge activeChallenge) {
        this.activeChallenge = activeChallenge;
    }

    public List<ActiveCurse> getActiveCurses() {
        return activeCurses;
    }

    public void setActiveCurses(List<ActiveCurse> activeCurses) {
        this.activeCurses = activeCurses != null ? activeCurses : new ArrayList<>();
    }

    public Long getVetoEndTime() {
        return vetoEndTime;
    }

    public void setVetoEndTime(Long vetoEndTime) {
        this.vetoEndTime = vetoEndTime;
    }

    public Long getHiderStartTime() {
        return hiderStartTime;
    }

    public void setHiderStartTime(Long hiderStartTime) {
        this.hiderStartTime = hiderStartTime;
    }

    public long getTotalHiderTime() {
        return totalHiderTime;
    }

    public void setTotalHiderTime(long totalHiderTime) {
        this.totalHiderTime = totalHiderTime;
    }

    public void addHiderTime(long timeToAdd) {
        this.totalHiderTime += timeToAdd;
    }
    
    // Location object that matches frontend structure
    public static class TeamLocation {
        private double latitude;
        private double longitude;
        private long timestamp;
        
        // Default constructor
        public TeamLocation() {}
        
        // Constructor with all fields
        public TeamLocation(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
        
        // Getters and Setters
        public double getLatitude() {
            return latitude;
        }
        
        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }
        
        public double getLongitude() {
            return longitude;
        }
        
        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
    
    // Convenience method for backend use
    public void setCurrentLocation(Location location) {
        if (location != null) {
            this.location = new TeamLocation(location.getLatitude(), location.getLongitude(), System.currentTimeMillis());
        }
    }
}
