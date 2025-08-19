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

// Simple shared connection manager per wsUrl+gameId to avoid multiple sockets per screen
type Manager = {
  key: string;
  ws: WebSocket | null;
  status: 'idle' | 'connecting' | 'open' | 'closed';
  subscribers: Set<(data: any) => void>;
  statusSubs: Set<(connected: boolean) => void>;
  reconnectTimer: ReturnType<typeof setTimeout> | null;
  heartbeatTimer: ReturnType<typeof setInterval> | null;
  backoff: number;
  shouldReconnect: boolean;
  connect: () => void;
  disconnect: () => void;
  send: (payload: any) => void;
  appStateSub: { remove: () => void } | null;
};

const managers = new Map<string, Manager>();

function getManager(key: string, wsUrl: string, gameId: string, heartbeatMs: number): Manager {
  const existing = managers.get(key);
  if (existing) return existing;

  const mgr: Manager = {
    key,
    ws: null,
    status: 'idle',
    subscribers: new Set(),
    statusSubs: new Set(),
    reconnectTimer: null,
    heartbeatTimer: null,
    backoff: 1000,
    shouldReconnect: true,
    connect: () => {},
    disconnect: () => {},
    send: (_: any) => {},
    appStateSub: null,
  };

  const clearTimers = () => {
    if (mgr.reconnectTimer) { clearTimeout(mgr.reconnectTimer); mgr.reconnectTimer = null; }
    if (mgr.heartbeatTimer) { clearInterval(mgr.heartbeatTimer); mgr.heartbeatTimer = null; }
  };

  const notifyStatus = (isOpen: boolean) => {
    mgr.statusSubs.forEach((fn) => {
      try { fn(isOpen); } catch {}
    });
  };

  const scheduleReconnect = () => {
    if (!mgr.shouldReconnect) return;
    if (mgr.reconnectTimer) return;
    const delay = Math.min(mgr.backoff, 30000);
    mgr.reconnectTimer = setTimeout(() => {
      mgr.reconnectTimer = null;
      mgr.backoff = Math.min(mgr.backoff * 2, 30000);
      mgr.connect();
    }, delay);
  };

  const startHeartbeat = () => {
    if (mgr.heartbeatTimer) return;
    mgr.heartbeatTimer = setInterval(() => {
      // Keep-alive ping; don't force-close if no pong (backend might not reply)
      try {
        if (mgr.ws && (mgr.ws as any).readyState === WebSocket.OPEN) {
          mgr.ws.send(JSON.stringify({ type: 'ping', t: Date.now() }));
        } else if (mgr.shouldReconnect) {
          // If WebSocket is not open but we should reconnect, try to reconnect
          console.log('WebSocket not open during heartbeat, attempting reconnect');
          mgr.connect();
        }
      } catch (e) {
        console.log('Heartbeat error:', e);
        if (mgr.shouldReconnect) {
          scheduleReconnect();
        }
      }
    }, Math.max(heartbeatMs, 25000));
  };

  mgr.connect = () => {
    if (!mgr.shouldReconnect) return;
    if (!gameId) return; // don't connect without a room id
    if (mgr.status === 'connecting' || (mgr.ws && (mgr.ws as any).readyState === WebSocket.OPEN)) {
      return;
    }
    try {
      // Close any stale socket
      try { mgr.ws?.close(); } catch {}
      mgr.status = 'connecting';
      const ws = new WebSocket(wsUrl);
      mgr.ws = ws;
      (ws as any).onopen = () => {
        mgr.status = 'open';
        mgr.backoff = 1000;
        notifyStatus(true);
        // Join room
        try { ws.send(JSON.stringify({ type: 'join', gameId })); } catch {}
        startHeartbeat();
      };
      (ws as any).onmessage = (ev: MessageEvent) => {
        try {
          const data = JSON.parse((ev as any).data);
          if (data?.type === 'pong') return;
          mgr.subscribers.forEach((fn) => { try { fn(data); } catch {} });
        } catch {}
      };
      const onCloseOrError = () => {
        mgr.status = 'closed';
        notifyStatus(false);
        clearTimers();
        scheduleReconnect();
      };
      (ws as any).onerror = onCloseOrError;
      (ws as any).onclose = onCloseOrError;
    } catch {
      scheduleReconnect();
    }
  };

  mgr.disconnect = () => {
    mgr.shouldReconnect = false;
    clearTimers();
    try { mgr.ws?.close(); } catch {}
    mgr.ws = null;
    mgr.status = 'closed';
    notifyStatus(false);
    try { mgr.appStateSub?.remove(); } catch {}
    mgr.appStateSub = null;
  };

  mgr.send = (payload: any) => {
    try {
      if (mgr.ws && (mgr.ws as any).readyState === WebSocket.OPEN) {
        mgr.ws.send(JSON.stringify(payload));
      }
    } catch {}
  };

  // Single AppState listener per manager
  mgr.appStateSub = AppState.addEventListener('change', (state: AppStateStatus) => {
    console.log('App state changed to:', state);
    if (state === 'active') {
      // Ensure connection on foreground
      console.log('App became active, checking WebSocket connection');
      if (!(mgr.ws && (mgr.ws as any).readyState === WebSocket.OPEN)) {
        console.log('WebSocket not connected, reconnecting');
        mgr.connect();
      } else {
        console.log('WebSocket already connected');
      }
    } else if (state === 'background') {
      // Keep connection; heartbeat will maintain it
      console.log('App went to background, maintaining WebSocket connection');
      // Send a ping to ensure the connection is still alive
      try {
        if (mgr.ws && (mgr.ws as any).readyState === WebSocket.OPEN) {
          mgr.ws.send(JSON.stringify({ type: 'ping', t: Date.now() }));
        }
      } catch (e) {
        console.log('Failed to send background ping:', e);
      }
    }
  });

  managers.set(key, mgr);
  // Kick off connection
  mgr.connect();
  return mgr;
}

export default function useGameWebSocket({
  wsUrl,
  gameId,
  onMessage,
  heartbeatMs = 30000,
  missAfterMs = 120000, // retained for API compat; no longer used to force close
}: Options) {
  const [connected, setConnected] = useState(false);
  const mgrRef = useRef<Manager | null>(null);

  const send = useCallback((payload: any) => {
    mgrRef.current?.send(payload);
  }, []);

  useEffect(() => {
    // Skip if no gameId to prevent unwanted connections
    if (!wsUrl || !gameId) return;
    const key = `${wsUrl}|${gameId}`;
    const mgr = getManager(key, wsUrl, gameId, heartbeatMs);
    mgrRef.current = mgr;

    const sub = (data: any) => {
      try { onMessage?.(data); } catch {}
    };
    const statusSub = (isOpen: boolean) => setConnected(isOpen);
    mgr.subscribers.add(sub);
    mgr.statusSubs.add(statusSub);

    // Ensure connected (idempotent)
    mgr.connect();

    return () => {
      mgr.subscribers.delete(sub);
      mgr.statusSubs.delete(statusSub);
      // Do not fully disconnect to allow other screens to keep using it
      // Manager will stay alive until app exit; optional: could add ref counting
    };
  }, [wsUrl, gameId, heartbeatMs, onMessage]);

  return { connected, send };
}
