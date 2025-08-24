import { useCallback } from 'react';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import ApiService from '../services/api';

/**
 * Hook for cleaning up push notifications when explicitly leaving games
 * This is useful for complete cleanup scenarios like app logout
 */
export function usePushCleanup() {
  const cleanupSpecificTeam = useCallback(async (gameId: string, teamId: string) => {
    try {
      if (!Device.isDevice) return;
      
      const projectId = Constants.expoConfig?.extra?.eas?.projectId;
      if (!projectId) return;
      
      const tokenResp = await Notifications.getExpoPushTokenAsync({ projectId });
      const token = tokenResp.data;
      if (!token) return;
      
      await ApiService.unregisterPushToken(gameId, teamId, token);
      console.log('Cleaned up push token for team:', teamId);
    } catch (e) {
      console.log('Push cleanup failed for team:', e);
    }
  }, []);

  const cleanupAllTeams = useCallback(async () => {
    try {
      if (!Device.isDevice) return;
      
      const projectId = Constants.expoConfig?.extra?.eas?.projectId;
      if (!projectId) return;
      
      const tokenResp = await Notifications.getExpoPushTokenAsync({ projectId });
      const token = tokenResp.data;
      if (!token) return;
      
      await ApiService.unregisterDeviceFromAllTeams(token);
      console.log('Cleaned up push token from all teams');
    } catch (e) {
      console.log('Complete push cleanup failed:', e);
    }
  }, []);

  return {
    cleanupSpecificTeam,
    cleanupAllTeams,
  };
}

export default usePushCleanup;
