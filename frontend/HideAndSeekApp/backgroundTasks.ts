import * as TaskManager from 'expo-task-manager';
import * as Location from 'expo-location';
import ApiService from './services/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

export const BACKGROUND_LOCATION_TASK = 'BACKGROUND_LOCATION_TASK';

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
    await ApiService.updateLocation(
      teamId,
      latest.coords.latitude,
      latest.coords.longitude,
      gameId
    );
  } catch (e) {
    // swallow errors in background
  }
});
