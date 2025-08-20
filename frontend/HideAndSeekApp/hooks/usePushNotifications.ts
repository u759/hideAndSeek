import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import ApiService from '../services/api';
import { Platform } from 'react-native';

// Configure foreground behavior for highest priority
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
    shouldShowAlert: true,
  }),
});

export default function usePushNotifications(gameId: string, teamId: string) {
  useEffect(() => {
    let mounted = true;
    let currentToken: string | null = null;
    let previousTeamId: string | null = null;
    
    // Add notification listeners
    const notificationListener = Notifications.addNotificationReceivedListener(notification => {
      console.log('Notification received:', notification);
    });

    const responseListener = Notifications.addNotificationResponseReceivedListener(response => {
      console.log('Notification response:', response);
    });
    
    const register = async () => {
      try {
        // Check if we're on a physical device
        if (!Device.isDevice) {
          console.log('Push notifications only work on physical devices');
          return;
        }

        // Android: ensure a notification channel exists with highest priority
        if (Platform.OS === 'android') {
          await Notifications.setNotificationChannelAsync('default', {
            name: 'UBCeek Game Updates',
            importance: Notifications.AndroidImportance.MAX,
            sound: 'default',
            vibrationPattern: [250, 250, 500],
            lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
            enableLights: true,
            enableVibrate: true,
            bypassDnd: true, // Bypass Do Not Disturb mode
            showBadge: true,
          });

          // Create a high priority channel specifically for game alerts
          await Notifications.setNotificationChannelAsync('game-alerts', {
            name: 'Game Alerts',
            importance: Notifications.AndroidImportance.MAX,
            sound: 'default',
            vibrationPattern: [500, 500, 500],
            lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
            enableLights: true,
            enableVibrate: true,
            bypassDnd: true,
            showBadge: true,
          });
        }

        // Ask permissions
        const { status: existingStatus } = await Notifications.getPermissionsAsync();
        let finalStatus = existingStatus;
        if (existingStatus !== 'granted') {
          const { status } = await Notifications.requestPermissionsAsync();
          finalStatus = status;
        }
        if (finalStatus !== 'granted') {
          console.log('Notification permission not granted');
          return;
        }

        // Get Expo push token (using Expo's service, not Firebase)
        const projectId = Constants.expoConfig?.extra?.eas?.projectId;
        if (!projectId) {
          console.log('No project ID found in expo config');
          return;
        }

        const tokenResp = await Notifications.getExpoPushTokenAsync({ 
          projectId: projectId 
        });
        const token = tokenResp.data;
        if (!mounted || !token) return;

        currentToken = token;
        console.log('Registering push token:', token);
        
        // If this is a team switch, the backend will automatically clean up the previous registration
        // Register with new team (this includes automatic cleanup of previous team)
        await ApiService.registerPushToken(gameId, teamId, token);
        console.log('Push token registered successfully for team:', teamId);
        previousTeamId = teamId;
      } catch (e) {
        // non-fatal
        console.log('Push registration failed', e);
      }
    };
    
    if (gameId && teamId) register();

    return () => {
      mounted = false;
      notificationListener.remove();
      responseListener.remove();
      
      // Optional: Unregister when component unmounts
      // Note: We don't unregister here because the user might just be navigating
      // The automatic cleanup on next registration is sufficient
    };
  }, [gameId, teamId]);
}
