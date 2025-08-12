package com.hideandseek.model;

public class PurchasedClue {
    private String id;
    private String clueTypeId;
    private String teamId;
    private String gameId;
    private String clueText;
    private int cost;
    private long timestamp;

    // Default constructor
    public PurchasedClue() {
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor with all fields
    public PurchasedClue(String id, String clueTypeId, String teamId, String gameId, String clueText, int cost) {
        this.id = id;
        this.clueTypeId = clueTypeId;
        this.teamId = teamId;
        this.gameId = gameId;
        this.clueText = clueText;
        this.cost = cost;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClueTypeId() {
        return clueTypeId;
    }

    public void setClueTypeId(String clueTypeId) {
        this.clueTypeId = clueTypeId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getClueText() {
        return clueText;
    }

    public void setClueText(String clueText) {
        this.clueText = clueText;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
