import { useState, useEffect, useRef, useCallback } from 'react';
import { Game, Team } from '../types';
import ApiService from '../services/api';
import useGameWebSocket from './useGameWebSocket';
import { getWebsocketUrl } from '../config/api';

interface UseGameStateOptions {
  gameId: string;
  teamId?: string;
  enabled?: boolean;
}

interface GameStateReturn {
  game: Game | null;
  currentTeam: Team | null;
  loading: boolean;
  error: string | null;
  connected: boolean;
  refresh: () => Promise<void>;
}

/**
 * Centralized game state management hook that uses WebSocket for real-time updates
 * and falls back to API calls when needed. Replaces individual polling patterns.
 */
export default function useGameState({ 
  gameId, 
  teamId, 
  enabled = true 
}: UseGameStateOptions): GameStateReturn {
  const [game, setGame] = useState<Game | null>(null);
  const [currentTeam, setCurrentTeam] = useState<Team | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const initialLoadRef = useRef(false);

  // WebSocket connection for real-time updates
  const wsUrl = getWebsocketUrl();
  const { connected, send } = useGameWebSocket({
    wsUrl: enabled ? wsUrl : '',
    gameId: enabled ? gameId : '',
    onMessage: (data: any) => {
      if (data?.type === 'gameUpdate' && data?.game) {
        console.log('Received WebSocket game update:', data.game.id);
        updateGameState(data.game);
      }
    },
    heartbeatMs: 30000,
  });

  // Update game state and extract current team
  const updateGameState = useCallback((newGame: Game) => {
    setGame(newGame);
    if (teamId) {
      const team = newGame.teams.find(t => t.id === teamId);
      setCurrentTeam(team || null);
    }
    setError(null);
  }, [teamId]);

  // Initial load and manual refresh function
  const refresh = useCallback(async () => {
    if (!enabled || !gameId) return;
    
    try {
      setLoading(true);
      setError(null);
      const gameData = await ApiService.getGame(gameId);
      updateGameState(gameData);
      console.log('Game state refreshed via API:', gameData.id);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to load game';
      setError(errorMessage);
      console.error('Failed to refresh game state:', err);
    } finally {
      setLoading(false);
    }
  }, [enabled, gameId, updateGameState]);

  // Initial load on mount
  useEffect(() => {
    if (enabled && gameId && !initialLoadRef.current) {
      initialLoadRef.current = true;
      refresh();
    }
  }, [enabled, gameId, refresh]);

  // Reset state when gameId changes
  useEffect(() => {
    if (gameId) {
      setGame(null);
      setCurrentTeam(null);
      setLoading(true);
      setError(null);
      initialLoadRef.current = false;
    }
  }, [gameId]);

  return {
    game,
    currentTeam,
    loading,
    error,
    connected,
    refresh,
  };
}
