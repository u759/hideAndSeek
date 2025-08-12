package com.hideandseek.model;

public class Clue {
    private String id;
    private String text;
    private int cost;
    
    // Default constructor
    public Clue() {}
    
    // Constructor with all fields
    public Clue(String id, String text, int cost) {
        this.id = id;
        this.text = text;
        this.cost = cost;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public int getCost() {
        return cost;
    }
    
    public void setCost(int cost) {
        this.cost = cost;
    }
}
