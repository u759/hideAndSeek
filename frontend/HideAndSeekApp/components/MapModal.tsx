import React from 'react';
import {
  Modal,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
  Pressable,
  Dimensions,
} from 'react-native';
import { WebView } from 'react-native-webview';

interface MapModalProps {
  visible: boolean;
  onClose: () => void;
  latitude: number;
  longitude: number;
  title?: string;
  teamName?: string;
}

const MapModal: React.FC<MapModalProps> = ({
  visible,
  onClose,
  latitude,
  longitude,
  title = 'Exact Location',
  teamName,
}) => {
  // Generate HTML content for OpenStreetMap with Leaflet
  const mapHtml = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
            integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
            crossorigin=""/>
      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
              integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
              crossorigin=""></script>
      <style>
        body { margin: 0; padding: 0; }
        #map { height: 100vh; width: 100vw; }
      </style>
    </head>
    <body>
      <div id="map"></div>
      <script>
        // Initialize the map centered on the target location
        var map = L.map('map').setView([${latitude}, ${longitude}], 18);
        
        // Add OpenStreetMap tiles
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© OpenStreetMap contributors',
          maxZoom: 19,
        }).addTo(map);
        
        // Add a marker for the exact location
        var marker = L.marker([${latitude}, ${longitude}]).addTo(map);
        
        // Add popup with team name if provided
        ${teamName ? `marker.bindPopup('${teamName} is here!').openPopup();` : `marker.bindPopup('Target location').openPopup();`}
        
        // Add click handler to recenter map on marker
        marker.on('click', function(e) {
          map.setView(e.latlng, 18);
        });
      </script>
    </body>
    </html>
  `;

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <SafeAreaView style={styles.backdrop}>
        <View style={styles.card}>
          <View style={styles.header}>
            <Text style={styles.title}>{title}</Text>
            {teamName && (
              <Text style={styles.subtitle}>Location of {teamName}</Text>
            )}
          </View>
          
          <View style={styles.coordinatesContainer}>
            <Text style={styles.coordinatesText}>
              {latitude.toFixed(6)}, {longitude.toFixed(6)}
            </Text>
          </View>
          
          <View style={styles.mapContainer}>
            <WebView
              source={{ html: mapHtml }}
              style={styles.webview}
              javaScriptEnabled={true}
              domStorageEnabled={true}
              startInLoadingState={true}
              scalesPageToFit={true}
              bounces={false}
              scrollEnabled={false}
            />
          </View>
          
          <View style={styles.buttonContainer}>
            <Pressable onPress={onClose} style={[styles.button, styles.primary]}>
              <Text style={styles.buttonText}>Close</Text>
            </Pressable>
          </View>
        </View>
      </SafeAreaView>
    </Modal>
  );
};

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  card: {
    width: '100%',
    maxWidth: 520,
    maxHeight: screenHeight * 0.85,
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowOffset: { width: 0, height: 4 },
    shadowRadius: 12,
    elevation: 4,
  },
  header: {
    marginBottom: 12,
    alignItems: 'center',
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#003366',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
  },
  coordinatesContainer: {
    backgroundColor: '#f5f5f5',
    padding: 8,
    borderRadius: 6,
    marginBottom: 12,
    alignItems: 'center',
  },
  coordinatesText: {
    fontSize: 12,
    fontFamily: 'monospace',
    color: '#333',
  },
  mapContainer: {
    height: 300,
    borderRadius: 8,
    overflow: 'hidden',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  webview: {
    flex: 1,
  },
  buttonContainer: {
    alignItems: 'flex-end',
  },
  button: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    minWidth: 120,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: {
    backgroundColor: '#003366',
  },
  buttonText: {
    color: 'white',
    fontWeight: '600',
  },
});

export default MapModal;
