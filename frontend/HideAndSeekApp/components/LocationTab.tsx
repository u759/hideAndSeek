import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  SafeAreaView,
  Alert,
  TouchableOpacity,
} from 'react-native';
import * as Location from 'expo-location';
import { Game, Team } from '../types';
import ApiService from '../services/api';
import useLocationTracker from '../hooks/useLocationTracker';

interface LocationTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const LocationTab: React.FC<LocationTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [location, setLocation] = useState<Location.LocationObject | null>(null);
  const [locationEnabled, setLocationEnabled] = useState(false);
  const [gpsActive, setGpsActive] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Add this hook to update location every 10s for both hiders and seekers
  // Seekers need location for distance-based clue targeting
  useLocationTracker({
    teamId: currentTeam.id,
    gameId: game.id,
    isHider: true, // Enable for all teams (hiders AND seekers)
    isActive: game.status === 'active',
    onLocationSent: (loc) => {
      setLocation(loc);
      setLastUpdate(new Date());
    },
  });

  useEffect(() => {
    requestLocationPermission();
    // Check GPS service status on mount and every 10s
    const checkGps = async () => {
      const enabled = await Location.hasServicesEnabledAsync();
      setGpsActive(enabled);
    };
    checkGps();
    const interval = setInterval(checkGps, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    // This effect now only requests the current location for display purposes
    // when the component mounts. The actual background tracking is handled
    // by the useLocationTracker hook.
    if (locationEnabled) {
      const fetchLocation = async () => {
        try {
          const currentLocation = await Location.getCurrentPositionAsync({
            accuracy: Location.Accuracy.High,
          });
          setLocation(currentLocation);
        } catch (error) {
          console.error('Failed to fetch location for display:', error);
        }
      };
      
      fetchLocation();
    }
  }, [locationEnabled]);

  const getCurrentLocationForDisplay = async () => {
    try {
      const currentLocation = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });
      setLocation(currentLocation);
    } catch (error) {
      console.error('Failed to get current location for display:', error);
    }
  };

  const requestLocationPermission = async () => {
    try {
      let { status } = await Location.requestForegroundPermissionsAsync();
      
      if (status !== 'granted') {
        setErrorMsg('Permission to access location was denied');
        Alert.alert(
          'Location Permission Required',
          'This app needs location access to share your position with seekers. Please enable location permissions in settings.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Settings', onPress: () => Location.requestForegroundPermissionsAsync() }
          ]
        );
        return;
      }

      // Check if location services are enabled
      const enabled = await Location.hasServicesEnabledAsync();
      if (!enabled) {
        setErrorMsg('Location services are disabled');
        Alert.alert(
          'Location Services Disabled',
          'Please enable location services in your device settings to participate as a hider.'
        );
        return;
      }

      setLocationEnabled(true);
    } catch (error) {
      setErrorMsg('Error requesting location permission');
      console.error('Location permission error:', error);
    }
  };

  const updateServerLocation = async (locationData: Location.LocationObject) => {
    try {
      await ApiService.updateLocation(
        currentTeam.id,
        locationData.coords.latitude,
        locationData.coords.longitude,
        game.id
      );
      setLastUpdate(new Date());
      console.log('Location updated successfully:', {
        teamId: currentTeam.id,
        gameId: game.id,
        coords: locationData.coords
      });
    } catch (error) {
      console.error('Failed to update server location:', error);
    }
  };

  const manualLocationUpdate = async () => {
    if (!locationEnabled) {
      Alert.alert('Location Disabled', 'Please enable location services first.');
      return;
    }

    try {
      const currentLocation = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });
      setLocation(currentLocation);
      try {
        await ApiService.updateLocation(
          currentTeam.id,
          currentLocation.coords.latitude,
          currentLocation.coords.longitude,
          game.id
        );
        setLastUpdate(new Date());
        Alert.alert('Success', 'Location updated successfully!');
      } catch (error: any) {
        // Show full error object for debugging
        let msg = 'Failed to update location.';
        if (error?.message) {
          msg = error.message;
        } else if (typeof error === 'object' && error !== null) {
          msg = JSON.stringify(error);
        } else if (error) {
          msg = String(error);
        }
        Alert.alert('Error', `Location update failed: ${msg}`);
        console.error('ERROR  Location update failed:', error);
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to get current location. Please try again.');
      console.error('Manual location fetch error:', error);
    }
  };

  const formatCoordinates = (lat: number, lon: number) => {
    return `${lat.toFixed(6)}, ${lon.toFixed(6)}`;
  };

  const isOnUBCCampus = (lat: number, lon: number) => {
    // UBC campus bounds (approximate)
    const ubcBounds = {
      north: 49.2720,
      south: 49.2580,
      east: -123.2370,
      west: -123.2640
    };
    
    return lat >= ubcBounds.south && lat <= ubcBounds.north &&
           lon >= ubcBounds.west && lon <= ubcBounds.east;
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Location Sharing</Text>
          <Text style={styles.subtitle}>
            {currentTeam.role === 'hider' 
              ? 'Your location is automatically shared with seekers'
              : 'Your location is required for distance-based clue targeting'
            }
          </Text>
        </View>

        <View style={styles.statusSection}>
          <Text style={styles.statusTitle}>Tracking Status</Text>
          <View style={[styles.statusIndicator, (game.status === 'active' && gpsActive) ? styles.statusActive : styles.statusInactive]}>
            <Text style={styles.statusText}>
              {(game.status === 'active' && gpsActive) ? '🟢 Active' : '🔴 Inactive'}
            </Text>
          </View>
        </View>

        {errorMsg ? (
          <View style={styles.errorSection}>
            <Text style={styles.errorTitle}>⚠️ Location Error</Text>
            <Text style={styles.errorText}>{errorMsg}</Text>
            <TouchableOpacity style={styles.retryButton} onPress={requestLocationPermission}>
              <Text style={styles.retryButtonText}>Retry</Text>
            </TouchableOpacity>
          </View>
        ) : location ? (
          <View style={styles.locationSection}>
            <Text style={styles.locationTitle}>Current Location</Text>
            <View style={styles.locationCard}>
              <View style={styles.coordinatesRow}>
                <Text style={styles.coordinatesLabel}>Coordinates:</Text>
                <Text style={styles.coordinatesValue}>
                  {formatCoordinates(location.coords.latitude, location.coords.longitude)}
                </Text>
              </View>
              
              <View style={styles.accuracyRow}>
                <Text style={styles.accuracyLabel}>Accuracy:</Text>
                <Text style={styles.accuracyValue}>
                  ±{location.coords.accuracy?.toFixed(0) || 'Unknown'}m
                </Text>
              </View>

              <View style={styles.campusRow}>
                <Text style={styles.campusLabel}>Campus Status:</Text>
                <Text style={[
                  styles.campusValue,
                  isOnUBCCampus(location.coords.latitude, location.coords.longitude) 
                    ? styles.onCampus 
                    : styles.offCampus
                ]}>
                  {isOnUBCCampus(location.coords.latitude, location.coords.longitude) 
                    ? '✅ On UBC Campus' 
                    : '❌ Off Campus'}
                </Text>
              </View>

              {lastUpdate && (
                <View style={styles.updateRow}>
                  <Text style={styles.updateLabel}>Last Update:</Text>
                  <Text style={styles.updateValue}>
                    {lastUpdate.toLocaleTimeString()}
                  </Text>
                </View>
              )}
            </View>

            <TouchableOpacity 
              style={[styles.updateButton, game.status !== 'active' && styles.updateButtonDisabled]}
              onPress={manualLocationUpdate}
              disabled={game.status !== 'active'}
            >
              <Text style={[styles.updateButtonText, game.status !== 'active' && styles.updateButtonTextDisabled]}>📍 Update Location Now</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={styles.loadingSection}>
            <Text style={styles.loadingText}>Getting your location...</Text>
          </View>
        )}

        <View style={styles.infoSection}>
          <Text style={styles.infoTitle}>How It Works</Text>
          <Text style={styles.infoText}>
            • Your location is automatically updated every 10 seconds
          </Text>
          <Text style={styles.infoText}>
            • Seekers can buy clues based on your current location
          </Text>
          <Text style={styles.infoText}>
            • Stay within UBC campus boundaries during the game
          </Text>
        </View>

        <View style={styles.tipsSection}>
          <Text style={styles.tipsTitle}>Hiding Tips</Text>
          <Text style={styles.tipText}>
            🏢 Use buildings and structures for cover
          </Text>
          <Text style={styles.tipText}>
            🌳 Natural features can provide good hiding spots
          </Text>
          <Text style={styles.tipText}>
            🚶‍♂️ You can move around, but no running allowed
          </Text>
          <Text style={styles.tipText}>
            🚫 Avoid restricted areas (washrooms, private offices)
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    flex: 1,
    padding: 16,
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#003366',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginTop: 4,
    textAlign: 'center',
  },
  statusSection: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statusTitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 12,
  },
  statusIndicator: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
  },
  statusActive: {
    backgroundColor: '#d4edda',
  },
  statusInactive: {
    backgroundColor: '#f8d7da',
  },
  statusText: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  errorSection: {
    backgroundColor: '#f8d7da',
    padding: 20,
    borderRadius: 8,
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#e74c3c',
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#721c24',
    marginBottom: 8,
  },
  errorText: {
    fontSize: 16,
    color: '#721c24',
    marginBottom: 16,
    lineHeight: 22,
  },
  retryButton: {
    backgroundColor: '#e74c3c',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 6,
    alignSelf: 'flex-start',
  },
  retryButtonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  locationSection: {
    marginBottom: 24,
  },
  locationTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 12,
  },
  locationCard: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  coordinatesRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  coordinatesLabel: {
    fontSize: 16,
    color: '#666',
  },
  coordinatesValue: {
    fontSize: 16,
    fontFamily: 'monospace',
    color: '#003366',
    fontWeight: 'bold',
  },
  accuracyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  accuracyLabel: {
    fontSize: 16,
    color: '#666',
  },
  accuracyValue: {
    fontSize: 16,
    color: '#333',
  },
  campusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  campusLabel: {
    fontSize: 16,
    color: '#666',
  },
  campusValue: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  onCampus: {
    color: '#27ae60',
  },
  offCampus: {
    color: '#e74c3c',
  },
  updateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  updateLabel: {
    fontSize: 16,
    color: '#666',
  },
  updateValue: {
    fontSize: 16,
    color: '#333',
  },
  updateButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  updateButtonDisabled: {
    backgroundColor: '#cccccc',
  },
  updateButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  updateButtonTextDisabled: {
    color: '#888',
  },
  loadingSection: {
    backgroundColor: '#fff',
    padding: 40,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 24,
  },
  loadingText: {
    fontSize: 16,
    color: '#666',
  },
  infoSection: {
    backgroundColor: '#e8f4fd',
    padding: 20,
    borderRadius: 8,
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#003366',
  },
  infoTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 12,
  },
  infoText: {
    fontSize: 14,
    color: '#333',
    marginBottom: 6,
    lineHeight: 20,
  },
  tipsSection: {
    backgroundColor: '#fff3cd',
    padding: 20,
    borderRadius: 8,
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#f39c12',
  },
  tipsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#856404',
    marginBottom: 12,
  },
  tipText: {
    fontSize: 14,
    color: '#856404',
    marginBottom: 8,
    lineHeight: 20,
  },
});

export default LocationTab;
