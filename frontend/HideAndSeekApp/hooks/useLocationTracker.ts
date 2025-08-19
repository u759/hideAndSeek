import { useEffect, useRef } from 'react';
import * as Location from 'expo-location';
import { Alert, AppState } from 'react-native';
import ApiService from '../services/api';
import * as TaskManager from 'expo-task-manager';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { BACKGROUND_LOCATION_TASK } from '../backgroundTasks';
import * as BackgroundFetch from 'expo-background-fetch';


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

    if (!shouldTrack || !teamId || !gameId) {
      console.log('Stopping location tracking - disabled or missing IDs');
      stopLocationTracking();
      return;
    }

    // Only track location when game is active
    if (isActive) {
      console.log('Starting location tracking (game is active)');
      startLocationTracking();
      // Also start background updates so tracking continues when app is backgrounded
      startBackgroundUpdates();
      // Register background fetch for additional reliability
      registerBackgroundFetch();
    } else {
      console.log('Stopping location tracking - game is not active (status: paused/waiting/ended)');
      stopLocationTracking();
      stopBackgroundUpdates();
      unregisterBackgroundFetch();
    }

    return () => {
      console.log('Cleaning up location tracking');
      stopLocationTracking();
      stopBackgroundUpdates();
      unregisterBackgroundFetch();
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

      console.log('Location permission granted, starting timer...');
      // Use timer-based updates as primary method to ensure updates every 10 seconds regardless of movement

      intervalRef.current = setInterval(async () => {
        try {
          console.log('Getting current position...');
          const location = await Location.getCurrentPositionAsync({
            accuracy: Location.Accuracy.High,
          });

          console.log('Sending location update to server...');
          await ApiService.updateLocation(
            teamId,
            location.coords.latitude,
            location.coords.longitude,
            gameId
          );
          console.log('Timer-based location update sent successfully:', location.coords);
          if (onLocationSent) {
            onLocationSent(location);
          }
        } catch (error) {
          console.error('Failed to send timer-based location update:', error);
        }
      }, 10000); // Every 10 seconds - this is the primary tracking method

      console.log('Location tracking started with 10-second timer');
      
    } catch (error) {
      console.error('Failed to start location tracking:', error);
      Alert.alert('Error', 'Failed to start location tracking. Please try again.');
    }
  };

  const stopLocationTracking = () => {
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
      const started = await Location.hasStartedLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
      if (!started) {
        await Location.startLocationUpdatesAsync(BACKGROUND_LOCATION_TASK, {
          accuracy: Location.Accuracy.Balanced,
          timeInterval: 15000, // 15s
          distanceInterval: 10,
          showsBackgroundLocationIndicator: true,
          foregroundService: {
            notificationTitle: 'UBCeek: location active',
            notificationBody: 'Sharing your location for gameplay',
            notificationColor: '#003366',
          },
        });
        console.log('Background location updates started');
      }
    } catch (e) {
      console.log('Failed to start background updates', e);
    }
  };

  const stopBackgroundUpdates = async () => {
    try {
      const started = await Location.hasStartedLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
      if (started) {
        await Location.stopLocationUpdatesAsync(BACKGROUND_LOCATION_TASK);
        console.log('Background location updates stopped');
      }
    } catch (e) {
      // ignore
    }
  };

  const registerBackgroundFetch = async () => {
    try {
      await BackgroundFetch.registerTaskAsync('BACKGROUND_SYNC_TASK', {
        minimumInterval: 60000, // 1 minute
        stopOnTerminate: false,
        startOnBoot: true,
      });
      console.log('Background fetch registered');
    } catch (e) {
      console.log('Failed to register background fetch', e);
    }
  };

  const unregisterBackgroundFetch = async () => {
    try {
      await BackgroundFetch.unregisterTaskAsync('BACKGROUND_SYNC_TASK');
      console.log('Background fetch unregistered');
    } catch (e) {
      // ignore
    }
  };

  return {
    startLocationTracking,
    stopLocationTracking,
  };
};

export default useLocationTracker;
