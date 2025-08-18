import { useEffect, useRef } from 'react';
import * as Location from 'expo-location';
import { Alert } from 'react-native';
import ApiService from '../services/api';


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
    } else {
      console.log('Stopping location tracking - game is not active (status: paused/waiting/ended)');
      stopLocationTracking();
    }

    return () => {
      console.log('Cleaning up location tracking');
      stopLocationTracking();
    };
  }, [isHider, isActive, teamId, gameId]);

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

  return {
    startLocationTracking,
    stopLocationTracking,
  };
};

export default useLocationTracker;
