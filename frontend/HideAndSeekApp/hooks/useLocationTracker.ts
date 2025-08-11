import { useEffect, useRef } from 'react';
import * as Location from 'expo-location';
import { Alert } from 'react-native';
import ApiService from '../services/api';

interface LocationTrackerProps {
  teamId: string;
  gameId: string;
  isHider: boolean;
  isActive: boolean;
}

const useLocationTracker = ({ teamId, gameId, isHider, isActive }: LocationTrackerProps) => {
  const locationSubscription = useRef<Location.LocationSubscription | null>(null);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!isHider || !isActive) {
      // Stop tracking if not a hider or game is not active
      stopLocationTracking();
      return;
    }

    startLocationTracking();

    return () => {
      stopLocationTracking();
    };
  }, [isHider, isActive, teamId, gameId]);

  const startLocationTracking = async () => {
    try {
      // Request location permissions
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert(
          'Location Permission Required',
          'Hiders must share their location to play. Please enable location access.'
        );
        return;
      }

      // Start watching location with high accuracy
      locationSubscription.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: 10000, // Update every 10 seconds
          distanceInterval: 10, // Update when moved 10 meters
        },
        async (location) => {
          try {
            await ApiService.updateLocation(
              teamId,
              location.coords.latitude,
              location.coords.longitude,
              gameId
            );
          } catch (error) {
            console.error('Failed to update server location:', error);
          }
        }
      );

      // Also send periodic updates to ensure location is fresh
      intervalRef.current = setInterval(async () => {
        try {
          const location = await Location.getCurrentPositionAsync({
            accuracy: Location.Accuracy.High,
          });
          
          await ApiService.updateLocation(
            teamId,
            location.coords.latitude,
            location.coords.longitude,
            gameId
          );
        } catch (error) {
          console.error('Failed to send periodic server location update:', error);
        }
      }, 30000); // Every 30 seconds
      
    } catch (error) {
      console.error('Failed to start location tracking:', error);
      Alert.alert('Error', 'Failed to start location tracking. Please try again.');
    }
  };

  const stopLocationTracking = () => {
    if (locationSubscription.current) {
      locationSubscription.current.remove();
      locationSubscription.current = null;
    }
    
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  return {
    startLocationTracking,
    stopLocationTracking,
  };
};

export default useLocationTracker;
