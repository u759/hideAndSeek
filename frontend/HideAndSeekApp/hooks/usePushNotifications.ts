import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';
import ApiService from '../services/api';

// Configure foreground behavior
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

export default function usePushNotifications(gameId: string, teamId: string) {
  useEffect(() => {
    let mounted = true;
    const register = async () => {
      try {
        // Ask permissions
        const { status: existingStatus } = await Notifications.getPermissionsAsync();
        let finalStatus = existingStatus;
        if (existingStatus !== 'granted') {
          const { status } = await Notifications.requestPermissionsAsync();
          finalStatus = status;
        }
        if (finalStatus !== 'granted') return;

        // Get Expo push token
        const projectId = (Constants as any)?.expoConfig?.extra?.eas?.projectId || (Constants as any)?.easConfig?.projectId;
        const tokenResp = await Notifications.getExpoPushTokenAsync({ projectId });
        const token = tokenResp.data;
        if (!mounted || !token) return;

        // Register on backend
        await ApiService.registerPushToken(gameId, teamId, token);
      } catch (e) {
        // non-fatal
        console.log('Push registration failed', e);
      }
    };
    if (gameId && teamId) register();
    return () => {
      mounted = false;
    };
  }, [gameId, teamId]);
}
