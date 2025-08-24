package com.hideandseek.logging;

import java.util.Map;

public class GameEvent {
    private String version;
    private String eventId;
    private String gameId;
    private long sequence;
    private long timestamp;
    private String timestampFormatted; // Human-readable timestamp, e.g., 2025-08-23 21:50:00
    private String type;
    private String actorType; // e.g., system, admin, team
    private String actorId;   // optional team or admin id
    private String actorName; // Human-readable name, e.g., Team Blue or Admin
    private Map<String, Object> payload;

    public GameEvent() {}

    public GameEvent(String version, String eventId, String gameId, long sequence, long timestamp,
                     String type, String actorType, String actorId, Map<String, Object> payload) {
        this.version = version;
        this.eventId = eventId;
        this.gameId = gameId;
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.type = type;
        this.actorType = actorType;
        this.actorId = actorId;
        this.payload = payload;
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getTimestampFormatted() { return timestampFormatted; }
    public void setTimestampFormatted(String timestampFormatted) { this.timestampFormatted = timestampFormatted; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
