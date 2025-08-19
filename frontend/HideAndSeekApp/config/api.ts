// Centralized API / WebSocket URL configuration
// Use a function wrapper so __DEV__ is evaluated at runtime in Expo
export const API_BASE_URL = (() => {
  if (typeof __DEV__ !== 'undefined' && __DEV__) {
    // Local development: point to local Spring Boot backend (update IP as needed)
    return 'http://192.168.1.147:3000/api';
  }
  // Production API base
  return 'https://hideandseek.ayng.dev/api';
})();

/**
 * Returns a WebSocket URL derived from API_BASE_URL.
 * - If API_BASE_URL starts with https, returns wss://<host>/ws
 * - If it starts with http, returns ws://<host>/ws
 * - Strips any trailing /api
 */
export function getWebsocketUrl(): string {
  try {
    // Remove trailing /api if present
    const base = API_BASE_URL.replace(/\/api\/?$/, '');
    if (base.startsWith('https')) {
      return base.replace(/^https/, 'wss') + '/ws';
    }
    return base.replace(/^http/, 'ws') + '/ws';
  } catch (e) {
    // Fallback: naive replace
    return API_BASE_URL.replace(/^http/, 'ws').replace(/\/api$/, '') + '/ws';
  }
}
