package com.hideandseek.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hideandseek.model.Game;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> gameConnections = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionGameMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket connection established: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            
            if ("join".equals(type)) {
                String gameId = (String) payload.get("gameId");
                joinGame(session, gameId);
            } else if ("leave".equals(type)) {
                String gameId = (String) payload.get("gameId");
                leaveGame(session, gameId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Invalid message format\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String gameId = sessionGameMap.get(session);
        if (gameId != null) {
            leaveGame(session, gameId);
        }
        System.out.println("WebSocket connection closed: " + session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket transport error for session " + session.getId() + ": " + exception.getMessage());
        String gameId = sessionGameMap.get(session);
        if (gameId != null) {
            leaveGame(session, gameId);
        }
    }

    public void joinGame(WebSocketSession session, String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) {
            return;
        }

        // Remove from previous game if any
        String previousGameId = sessionGameMap.get(session);
        if (previousGameId != null) {
            leaveGame(session, previousGameId);
        }

        // Add to new game
        gameConnections.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionGameMap.put(session, gameId);

        System.out.println("Session " + session.getId() + " joined game " + gameId);
    }

    public void leaveGame(WebSocketSession session, String gameId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = gameConnections.get(gameId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                gameConnections.remove(gameId);
            }
        }
        sessionGameMap.remove(session);

        System.out.println("Session " + session.getId() + " left game " + gameId);
    }

    public void broadcastToGame(String gameId, Game game) {
        CopyOnWriteArraySet<WebSocketSession> sessions = gameConnections.get(gameId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String gameJson = objectMapper.writeValueAsString(Map.of(
                "type", "gameUpdate",
                "game", game
            ));

            TextMessage message = new TextMessage(gameJson);
            
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
                    // Remove broken session
                    sessions.remove(session);
                    sessionGameMap.remove(session);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getActiveConnections(String gameId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = gameConnections.get(gameId);
        return sessions != null ? sessions.size() : 0;
    }
}
