import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, AppStateStatus } from 'react-native';

type Options = {
  wsUrl: string;
  gameId: string;
  onMessage?: (data: any) => void;
  // Heartbeat every heartbeatMs; reconnect if no pong/activity for missAfterMs
  heartbeatMs?: number;
  missAfterMs?: number;
};

export default function useGameWebSocket({
  wsUrl,
  gameId,
  onMessage,
  heartbeatMs = 15000,
  missAfterMs = 25000,
}: Options) {
  const wsRef = useRef<WebSocket | null>(null);
  const [connected, setConnected] = useState(false);
  const shouldReconnectRef = useRef(true);
  const backoffRef = useRef(1000); // start at 1s, max 30s
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const heartbeatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastActivityRef = useRef<number>(Date.now());

  const clearTimers = () => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    if (heartbeatTimerRef.current) {
      clearInterval(heartbeatTimerRef.current);
      heartbeatTimerRef.current = null;
    }
  };

  const send = useCallback((payload: any) => {
    try {
      if (wsRef.current && (wsRef.current as any).readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify(payload));
      }
    } catch (_) {
      // ignore
    }
  }, []);

  const startHeartbeat = useCallback(() => {
    if (heartbeatTimerRef.current) return;
    heartbeatTimerRef.current = setInterval(() => {
      const now = Date.now();
      // If we've been inactive (no message/pong) for too long, force reconnect
      if (now - lastActivityRef.current > missAfterMs) {
        try { wsRef.current?.close(); } catch {}
        return;
      }
      // Send ping
      send({ type: 'ping', t: Date.now() });
    }, heartbeatMs);
  }, [heartbeatMs, missAfterMs, send]);

  const scheduleReconnect = useCallback(() => {
    if (!shouldReconnectRef.current) return;
    if (reconnectTimerRef.current) return; // already scheduled
    const delay = Math.min(backoffRef.current, 30000);
    reconnectTimerRef.current = setTimeout(() => {
      reconnectTimerRef.current = null;
      backoffRef.current = Math.min(backoffRef.current * 2, 30000);
      connect();
    }, delay);
  }, []);

  const onOpen = useCallback(() => {
    setConnected(true);
    backoffRef.current = 1000; // reset backoff
    lastActivityRef.current = Date.now();
    // join game room
    send({ type: 'join', gameId });
    startHeartbeat();
  }, [gameId, send, startHeartbeat]);

  const onCloseOrError = useCallback(() => {
    setConnected(false);
    clearTimers();
    scheduleReconnect();
  }, [scheduleReconnect]);

  const connect = useCallback(() => {
    try {
  // Close any existing socket before opening a new one
  try { wsRef.current?.close(); } catch {}
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;
      (ws as any).onopen = onOpen;
      (ws as any).onmessage = (ev: MessageEvent) => {
        lastActivityRef.current = Date.now();
        try {
          const data = JSON.parse((ev as any).data);
          if (data?.type !== 'pong') {
            onMessage?.(data);
          }
        } catch {
          // ignore malformed
        }
      };
      (ws as any).onerror = onCloseOrError;
      (ws as any).onclose = onCloseOrError;
    } catch {
      onCloseOrError();
    }
  }, [onCloseOrError, onOpen, onMessage, wsUrl]);

  useEffect(() => {
    shouldReconnectRef.current = true;
    connect();
    const sub = AppState.addEventListener('change', (state: AppStateStatus) => {
      if (state === 'active') {
        // when returning to foreground, ensure connectivity
        if (!(wsRef.current && (wsRef.current as any).readyState === WebSocket.OPEN)) {
          clearTimers();
          connect();
        }
      }
    });

    return () => {
      shouldReconnectRef.current = false;
      clearTimers();
      try { wsRef.current && send({ type: 'leave', gameId }); } catch {}
      try { wsRef.current?.close(); } catch {}
      wsRef.current = null;
      sub.remove();
    };
  }, [gameId, wsUrl]);

  return { connected, send };
}
