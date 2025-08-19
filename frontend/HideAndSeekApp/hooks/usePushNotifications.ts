import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import ApiService from '../services/api';
import { Platform } from 'react-native';

// Configure foreground behavior
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

export default function usePushNotifications(gameId: string, teamId: string) {
  useEffect(() => {
    let mounted = true;
    
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

        // Android: ensure a notification channel exists
        if (Platform.OS === 'android') {
          await Notifications.setNotificationChannelAsync('default', {
            name: 'General',
            importance: Notifications.AndroidImportance.MAX,
            sound: 'default',
            vibrationPattern: [250, 250, 500],
            lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
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

        console.log('Registering push token:', token);
        // Register on backend
        await ApiService.registerPushToken(gameId, teamId, token);
        console.log('Push token registered successfully');
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
    };
  }, [gameId, teamId]);
}
