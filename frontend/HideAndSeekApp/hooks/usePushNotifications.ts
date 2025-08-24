import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import ApiService from '../services/api';
import { Platform } from 'react-native';
import { useAudioPlayer, setAudioModeAsync } from 'expo-audio';

// Configure foreground behavior for MAXIMUM priority with all alerts enabled
Notifications.setNotificationHandler({
  handleNotification: async (notification) => {
    // ALL game notifications get maximum priority for guaranteed attention
    
    return {
      shouldPlaySound: true,
      shouldSetBadge: true,
      shouldShowBanner: true,
      shouldShowList: true,
      // ⚡ MAXIMUM priority for ALL notifications to guarantee user attention
      priority: Notifications.AndroidNotificationPriority.MAX,
    };
  },
});

// Exported helper to create Android notification channels early (call from App startup)
export async function createNotificationChannelsAsync() {
  try {
    if (Platform.OS !== 'android') return;

    // Default channel for regular notifications - UPGRADED TO MAX
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Game Updates',
      importance: Notifications.AndroidImportance.MAX,
      sound: 'notif.wav',
      vibrationPattern: [250, 250, 250],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
      enableLights: true,
      enableVibrate: true,
      bypassDnd: true,
      showBadge: true,
      description: 'Regular game updates and notifications',
      groupId: 'ubceek_game',
      lightColor: '#003366',
    });

    await Notifications.setNotificationChannelAsync('urgent', {
      name: 'Urgent Notifications',
      importance: Notifications.AndroidImportance.MAX,
      sound: 'notif.wav',
      vibrationPattern: [0, 250, 250, 250],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
      enableLights: true,
      enableVibrate: true,
      bypassDnd: true,
      showBadge: true,
      description: 'Urgent game notifications requiring immediate attention',
      groupId: 'ubceek_urgent',
      lightColor: '#FF0000',
    });

    await Notifications.setNotificationChannelAsync('game-alerts', {
      name: 'Game Alerts',
      importance: Notifications.AndroidImportance.MAX,
      sound: 'notif.wav',
      vibrationPattern: [500, 500, 500],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
      enableLights: true,
      enableVibrate: true,
      bypassDnd: true,
      showBadge: true,
      description: 'Important game events and clue notifications',
      groupId: 'ubceek_game',
      lightColor: '#007acc',
    });

    await Notifications.setNotificationChannelAsync('emergency', {
      name: 'Emergency Alerts',
      importance: Notifications.AndroidImportance.MAX,
      sound: 'notif.wav',
      vibrationPattern: [1000, 1000, 1000],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
      enableLights: true,
      enableVibrate: true,
      bypassDnd: true,
      showBadge: true,
      description: 'Emergency game notifications that bypass all restrictions',
      groupId: 'ubceek_emergency',
      lightColor: '#FF0000',
    });

    await Notifications.setNotificationChannelAsync('heads-up', {
      name: 'Heads-Up Alerts',
      importance: Notifications.AndroidImportance.MAX,
      sound: 'notif.wav',
      vibrationPattern: [0, 500, 100, 500, 100, 500],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
      enableLights: true,
      enableVibrate: true,
      bypassDnd: true,
      showBadge: true,
      description: 'Maximum attention notifications that appear as heads-up display',
      groupId: 'ubceek_headsup',
      lightColor: '#FF6600',
    });

    // Dedicated channel for curse events with a distinct sound
    await Notifications.setNotificationChannelAsync('curse', {
      name: 'Curse Alerts',
      importance: Notifications.AndroidImportance.MAX,
      sound: 'veto_curse.wav',
      vibrationPattern: [0, 400, 200, 400, 200, 400],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
      enableLights: true,
      enableVibrate: true,
      bypassDnd: true,
      showBadge: true,
      description: 'Alerts when a hider is cursed',
      groupId: 'ubceek_game',
      lightColor: '#AA0000',
    });
  } catch (e) {
    console.log('Failed to create notification channels', e);
  }
}

// Optional handlers allow the app root to respond when user taps a notification that launched / resumed the app.
// onReceiveForeground: fires when a notification arrives while app is foreground.
// onRespond: fires when user taps a notification (cold start, background, or foreground).
export default function usePushNotifications(
  gameId: string,
  teamId: string,
  opts?: {
    onReceiveForeground?: (notification: Notifications.Notification) => void;
    onRespond?: (response: Notifications.NotificationResponse) => void;
  }
) {
  // Prepare curse sound player (expo-audio)
  const cursePlayer = useAudioPlayer(require('../assets/veto_curse.wav'));
  const playCurseSound = () => {
    try {
      cursePlayer.seekTo(0);
      cursePlayer.play();
    } catch {}
  };

  useEffect(() => {
    let mounted = true;
    let currentToken: string | null = null;
    let previousTeamId: string | null = null;
    // Ensure playback allowed in silent mode
    setAudioModeAsync({ playsInSilentMode: true }).catch(() => {});
    
    // Add notification listeners
    const notificationListener = Notifications.addNotificationReceivedListener(notification => {
      // Foreground receipt only (when app visible). Background receipts are auto-displayed by OS.
      console.log('Notification received (foreground):', notification?.request?.content?.data || notification);
      try {
        const data: any = notification?.request?.content?.data || {};
        // If this is a curse event, play the distinct local sound immediately
        if (data?.type === 'hider_cursed' || data?.event === 'hider_cursed') {
          playCurseSound();
        }
      } catch {}
      opts?.onReceiveForeground?.(notification);
    });

    const responseListener = Notifications.addNotificationResponseReceivedListener(response => {
      // Fires when user taps a notification from background OR foreground or from tray while cold.
      console.log('Notification response (tap):', response?.notification?.request?.content?.data || response);
      opts?.onRespond?.(response);
    });

    // Handle cold start (app opened from a notification before listeners attached)
    (async () => {
      try {
        const lastResponse = await Notifications.getLastNotificationResponseAsync();
        if (lastResponse) {
          console.log('Cold start via notification:', lastResponse.notification.request.content.data);
          opts?.onRespond?.(lastResponse);
        }
      } catch (e) {
        // non-fatal
      }
    })();
    
    const register = async () => {
      try {
        // Check if we're on a physical device
        if (!Device.isDevice) {
          console.log('Push notifications only work on physical devices');
          return;
        }

        // Android: ensure notification channels exist with proper hierarchy (following ChatGPT/SO guidelines)
  // Ensure channels exist (created at app startup as well)
  await createNotificationChannelsAsync();

        // Ask permissions with critical settings (following latest Expo docs)
        const { status: existingStatus } = await Notifications.getPermissionsAsync();
        let finalStatus = existingStatus;
        if (existingStatus !== 'granted') {
          const { status } = await Notifications.requestPermissionsAsync({
            ios: {
              allowAlert: true,
              allowBadge: true,
              allowSound: true,
              allowDisplayInCarPlay: true,
              allowCriticalAlerts: true, // Critical alerts bypass Focus/DND
              allowProvisional: false, // We want explicit permission
              provideAppNotificationSettings: true,
            },
            android: {
              // Android permissions are handled automatically via channels
            },
          });
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
