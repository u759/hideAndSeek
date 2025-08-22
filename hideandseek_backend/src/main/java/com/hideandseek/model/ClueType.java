package com.hideandseek.model;

public class ClueType {
    private String id;
    private String name;
    private String description;
    private int cost;
    private Integer range; // Range in meters, null means unlimited range

    // Default constructor
    public ClueType() {}

    // Constructor with all fields
    public ClueType(String id, String name, String description, int cost, Integer range) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cost = cost;
        this.range = range;
    }

    // Getters and Setters
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public Integer getRange() {
        return range;
    }

    public void setRange(Integer range) {
        this.range = range;
    }
}
