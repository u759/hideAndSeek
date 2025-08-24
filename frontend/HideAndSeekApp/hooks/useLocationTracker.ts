import { useEffect, useRef } from 'react';
import * as Location from 'expo-location';
import { Alert, AppState } from 'react-native';
import ApiService from '../services/api';
import * as TaskManager from 'expo-task-manager';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { BACKGROUND_LOCATION_TASK } from '../backgroundTasks';
import * as BackgroundTask from 'expo-background-task';


interface LocationTrackerProps {
  teamId: string;
  gameId: string;
  // Back-compat: if isHider is true, tracking is enabled (original behavior)
  // New: enabled allows tracking for any role (e.g., seekers)
  isHider?: boolean;
  enabled?: boolean;
  isActive: boolean;
  onLocationSent?: (location: Location.LocationObject) => void;
}

const useLocationTracker = ({ teamId, gameId, isHider, enabled, isActive, onLocationSent }: LocationTrackerProps) => {
  const locationSubscription = useRef<Location.LocationSubscription | null>(null);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const appStateRef = useRef(AppState.currentState);

  useEffect(() => {
    const shouldTrack = Boolean(enabled) || Boolean(isHider);
    console.log('useLocationTracker effect triggered:', { shouldTrack, isActive, teamId, gameId });

    const setupTracking = async () => {
      if (!shouldTrack || !teamId || !gameId) {
        console.log('Stopping location tracking - disabled or missing IDs');
        await stopLocationTracking();
        return;
      }

      // Only track location when game is active
      if (isActive) {
        console.log('Starting location tracking (game is active)');
        await startLocationTracking();
        // Also start background updates so tracking continues when app is backgrounded
        await startBackgroundUpdates();
        // Register background fetch for additional reliability
        await registerBackgroundTask();
      } else {
        console.log('Stopping location tracking - game is not active (status: paused/waiting/ended)');
        await stopLocationTracking();
        await stopBackgroundUpdates();
        await unregisterBackgroundTask();
      }
    };

    setupTracking();

    return () => {
      const cleanup = async () => {
        console.log('Cleaning up location tracking');
        await stopLocationTracking();
        await stopBackgroundUpdates();
        await unregisterBackgroundTask();
      };
      cleanup();
    };
  }, [isHider, enabled, isActive, teamId, gameId]);

  const startLocationTracking = async () => {
    try {
      console.log('Requesting location permissions...');
      // Request location permissions
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        console.log('Location permission denied');
        Alert.alert(
          'Location Permission Required',
          'Location access is required for gameplay. Please enable location access.'
        );
        return;
      }

      console.log('Location permission granted, starting background location updates...');
      // Use background location updates which survive app backgrounding
      const started = await Location.hasStartedLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
      if (!started) {
        await Location.startLocationUpdatesAsync(BACKGROUND_LOCATION_TASK, {
          accuracy: Location.Accuracy.High,
          timeInterval: 10000, // Every 10 seconds
          distanceInterval: 0, // Update regardless of movement
          showsBackgroundLocationIndicator: true,
          foregroundService: {
            notificationTitle: 'UBCeek: Location Active',
            notificationBody: 'Sharing your location for gameplay',
            notificationColor: '#003366',
          },
        });
        console.log('Background location updates started');
      }

      // Also start foreground timer as backup for immediate updates when app is active
      intervalRef.current = setInterval(async () => {
        try {
          console.log('Getting current position (foreground backup)...');
          const location = await Location.getCurrentPositionAsync({
            accuracy: Location.Accuracy.High,
          });

          console.log('Sending foreground location update to server...');
          await ApiService.updateLocation(
            teamId,
            location.coords.latitude,
            location.coords.longitude,
            gameId
          );
          console.log('Foreground location update sent successfully:', location.coords);
          if (onLocationSent) {
            onLocationSent(location);
          }
        } catch (error) {
          console.error('Failed to send foreground location update:', error);
        }
      }, 10000); // Every 10 seconds - backup for when app is active

      console.log('Location tracking started with background updates + foreground timer');
      
    } catch (error) {
      console.error('Failed to start location tracking:', error);
      Alert.alert('Error', 'Failed to start location tracking. Please try again.');
    }
  };

  const stopLocationTracking = async () => {
    console.log('Stopping location tracking...');
    if (locationSubscription.current) {
      locationSubscription.current.remove();
      locationSubscription.current = null;
      console.log('Location subscription removed');
    }
    
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
      console.log('Location timer cleared');
    }

    // Stop background location updates
    try {
      const started = await Location.hasStartedLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
      if (started) {
        await Location.stopLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
        console.log('Background location updates stopped');
      } else {
        console.log('Background location updates not active, nothing to stop');
      }
    } catch (error) {
      // Suppress TaskNotFoundException - it means the task was already stopped or never started
      const errorMessage = error instanceof Error ? error.message : String(error);
      if (errorMessage.includes('TaskNotFoundException')) {
        console.log('Background location task was not found (already stopped or never started)');
      } else {
        console.error('Error stopping background location updates:', error);
      }
    }
  };

  const startBackgroundUpdates = async () => {
    try {
      const { status: fg } = await Location.requestForegroundPermissionsAsync();
      const { status: bg } = await Location.requestBackgroundPermissionsAsync();
      if (fg !== 'granted' || bg !== 'granted') {
        console.log('Background location permission not granted');
        return;
      }
      // Persist IDs for the background task to access
      await AsyncStorage.setItem('bg_teamId', teamId);
      await AsyncStorage.setItem('bg_gameId', gameId);
      
      // Background updates are now handled in startLocationTracking via BACKGROUND_LOCATION_TASK
      console.log('Background permissions granted and IDs stored');
    } catch (e) {
      console.log('Failed to prepare background updates', e);
    }
  };

  const stopBackgroundUpdates = async () => {
    // Background updates are now handled in stopLocationTracking
    // Just clean up stored IDs
    try {
      await AsyncStorage.removeItem('bg_teamId');
      await AsyncStorage.removeItem('bg_gameId');
      console.log('Background update IDs cleared');
    } catch (e) {
      // ignore
    }
  };

  const registerBackgroundTask = async () => {
    try {
      await BackgroundTask.registerTaskAsync('BACKGROUND_SYNC_TASK', {
        // Android minimum is 15 minutes; system may delay further
        minimumInterval: 15, // minutes
      });
      console.log('Background task registered');
    } catch (e) {
      console.log('Failed to register background task', e);
    }
  };

  const unregisterBackgroundTask = async () => {
    try {
      await BackgroundTask.unregisterTaskAsync('BACKGROUND_SYNC_TASK');
      console.log('Background task unregistered');
    } catch (e) {
      // ignore
    }
  };

  return {
    startLocationTracking,
    stopLocationTracking: () => stopLocationTracking(),
  };
};

export default useLocationTracker;