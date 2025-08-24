import { useEffect, useRef } from 'react';
import * as Location from 'expo-location';
import { Alert } from 'react-native';
import ApiService from '../services/api';

interface LocationTrackerProps {
teamId: string;
gameId: string;
isHider?: boolean;
enabled?: boolean;
isActive: boolean;
onLocationSent?: (location: Location.LocationObject) => void;
}

const useLocationTracker = ({ teamId, gameId, isHider, enabled, isActive, onLocationSent }: LocationTrackerProps) => {
  const locationSubscription = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    const shouldTrack = Boolean(enabled) || Boolean(isHider);
    console.log('useLocationTracker effect triggered:', { shouldTrack, isActive, teamId, gameId });

    const setupTracking = async () => {
      if (!shouldTrack || !teamId || !gameId) {
        console.log('Stopping location tracking - disabled or missing IDs');
        await stopLocationTracking();
        return;
      }

      if (isActive) {
        console.log('Starting foreground location tracking (game is active)');
        await startLocationTracking();
      } else {
        console.log('Stopping location tracking - game is not active');
        await stopLocationTracking();
      }
    };

    setupTracking();

    return () => {
      console.log('Cleaning up location tracking');
      stopLocationTracking();
    };
  }, [isHider, enabled, isActive, teamId, gameId]);

  const startLocationTracking = async () => {
    try {
      console.log('Requesting foreground location permissions...');
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        console.log('Foreground location permission denied');
        Alert.alert(
          'Location Permission Required',
          'Location access is required for gameplay. Please enable location access.'
        );
        return;
      }

      console.log('Foreground location permission granted, starting updates...');
      locationSubscription.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: 10000,
          distanceInterval: 0,
        },
        async (location) => {
          console.log('Sending foreground location update to server...');
          try {
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
        }
      );
    } catch (error) {
      console.error('Failed to start location tracking:', error);
      Alert.alert('Error', 'Failed to start location tracking. Please try again.');
    }
  };

  const stopLocationTracking = () => {
    console.log('Stopping foreground location tracking...');
    if (locationSubscription.current) {
      locationSubscription.current.remove();
      locationSubscription.current = null;
      console.log('Location subscription removed');
    }
  };

  return {
    startLocationTracking,
    stopLocationTracking,
  };
};

export default useLocationTracker;