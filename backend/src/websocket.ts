import { WebSocketServer, WebSocket } from 'ws';

interface ConnectedClient {
  ws: WebSocket;
  teamId?: string;
  gameId?: string;
}

const clients: Map<string, ConnectedClient> = new Map();

export function setupWebSocket(wss: WebSocketServer) {
  wss.on('connection', (ws: WebSocket) => {
    const clientId = generateClientId();
    clients.set(clientId, { ws });
    
    console.log(`Client connected: ${clientId}`);
    
    ws.on('message', (data: Buffer) => {
      try {
        const message = JSON.parse(data.toString());
        handleMessage(clientId, message);
      } catch (error) {
        console.error('Invalid message format:', error);
      }
    });
    
    ws.on('close', () => {
      clients.delete(clientId);
      console.log(`Client disconnected: ${clientId}`);
    });
    
    ws.on('error', (error) => {
      console.error(`WebSocket error for client ${clientId}:`, error);
    });
  });
}

function handleMessage(clientId: string, message: any) {
  const client = clients.get(clientId);
  if (!client) return;
  
  switch (message.type) {
    case 'join_game':
      client.teamId = message.teamId;
      client.gameId = message.gameId;
      broadcastToGame(message.gameId, {
        type: 'team_joined',
        teamId: message.teamId
      }, clientId);
      break;
      
    case 'location_update':
      broadcastToGame(message.gameId, {
        type: 'location_updated',
        teamId: client.teamId,
        location: message.location
      }, clientId);
      break;
      
    case 'challenge_completed':
      broadcastToGame(message.gameId, {
        type: 'challenge_completed',
        teamId: client.teamId,
        challenge: message.challenge,
        tokensEarned: message.tokensEarned
      }, clientId);
      break;
      
    case 'curse_activated':
      broadcastToGame(message.gameId, {
        type: 'curse_activated',
        targetTeamId: message.targetTeamId,
        curse: message.curse
      }, clientId);
      break;
  }
}

function broadcastToGame(gameId: string, message: any, excludeClientId?: string) {
  for (const [clientId, client] of clients.entries()) {
    if (client.gameId === gameId && clientId !== excludeClientId) {
      try {
        client.ws.send(JSON.stringify(message));
      } catch (error) {
        console.error(`Failed to send message to client ${clientId}:`, error);
      }
    }
  }
}

export function sendToTeam(gameId: string, teamId: string, message: any) {
  for (const [clientId, client] of clients.entries()) {
    if (client.gameId === gameId && client.teamId === teamId) {
      try {
        client.ws.send(JSON.stringify(message));
      } catch (error) {
        console.error(`Failed to send message to team ${teamId}:`, error);
      }
    }
  }
}

function generateClientId(): string {
  return Math.random().toString(36).substr(2, 9);
}
