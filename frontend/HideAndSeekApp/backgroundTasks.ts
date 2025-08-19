import * as TaskManager from 'expo-task-manager';
import * as Location from 'expo-location';
import * as Notifications from 'expo-notifications';
import * as BackgroundFetch from 'expo-background-fetch';
import ApiService from './services/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

export const BACKGROUND_LOCATION_TASK = 'BACKGROUND_LOCATION_TASK';
export const BACKGROUND_SYNC_TASK = 'BACKGROUND_SYNC_TASK';

// Background location task
TaskManager.defineTask(BACKGROUND_LOCATION_TASK, async ({ data, error }: { data?: any; error?: TaskManager.TaskManagerError | null }) => {
  if (error) {
    console.warn('Background location error:', error);
    return;
  }
  const { locations } = (data || {}) as { locations?: Location.LocationObject[] };
  if (!locations || locations.length === 0) return;

  try {
    const teamId = await AsyncStorage.getItem('bg_teamId');
    const gameId = await AsyncStorage.getItem('bg_gameId');
    if (!teamId || !gameId) return;

    const latest = locations[locations.length - 1];
    console.log('Background location update:', latest.coords);
    
    await ApiService.updateLocation(
      teamId,
      latest.coords.latitude,
      latest.coords.longitude,
      gameId
    );
    
    // Update last sync time
    await AsyncStorage.setItem('last_location_sync', Date.now().toString());
  } catch (e) {
    console.log('Background location update failed:', e);
    // swallow errors in background
  }
});

// Background sync task to check for game updates and send local notifications
TaskManager.defineTask(BACKGROUND_SYNC_TASK, async () => {
  try {
    const teamId = await AsyncStorage.getItem('bg_teamId');
    const gameId = await AsyncStorage.getItem('bg_gameId');
    if (!teamId || !gameId) return;

    console.log('Background sync task running');

    // Check for game state changes
    const game = await ApiService.getGame(gameId);
    const lastSyncTime = await AsyncStorage.getItem('last_sync_time') || '0';
    const lastSync = parseInt(lastSyncTime);
    const now = Date.now();
    
    // If it's been more than 30 seconds since last sync, trigger a check
    if (now - lastSync > 30000) {
      await AsyncStorage.setItem('last_sync_time', now.toString());
      
      // Check if we need to send a notification about game state changes
      const lastGameStatus = await AsyncStorage.getItem('last_game_status');
      if (lastGameStatus && lastGameStatus !== game.status) {
        await Notifications.scheduleNotificationAsync({
          content: {
            title: 'UBCeek Game Update',
            body: `Game status changed to: ${game.status}`,
            sound: 'default',
          },
          trigger: null, // Send immediately
        });
      }
      await AsyncStorage.setItem('last_game_status', game.status);
      
      // Force a location update if we're a hider and game is active
      if (game.status === 'active') {
        const myTeam = game.teams.find(t => t.id === teamId);
        if (myTeam?.role === 'hider') {
          try {
            const location = await Location.getCurrentPositionAsync({
              accuracy: Location.Accuracy.Balanced,
            });
            await ApiService.updateLocation(
              teamId,
              location.coords.latitude,
              location.coords.longitude,
              gameId
            );
          } catch (e) {
            // ignore location errors in background
          }
        }
      }
    }
    
    return BackgroundFetch.BackgroundFetchResult.NewData;
  } catch (e) {
    console.log('Background sync error:', e);
    return BackgroundFetch.BackgroundFetchResult.Failed;
  }
});
