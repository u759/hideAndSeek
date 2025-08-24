import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  Alert,
  FlatList,
  Image,
  Dimensions,
} from 'react-native';
import { Game, Team, ClueType, Clue, HiderClueData } from '../types';
import * as ExpoLocation from 'expo-location';
import ApiService from '../services/api';
import useLocationTracker from '../hooks/useLocationTracker';
import { API_BASE_URL } from '../config/api';
import useGameWebSocket from '../hooks/useGameWebSocket';
import { getWebsocketUrl } from '../config/api';
import MapModal from './MapModal';

interface CluesTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const CluesTab: React.FC<CluesTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [clueTypes, setClueTypes] = useState<ClueType[]>([]);
  const [purchasedClues, setPurchasedClues] = useState<Clue[]>([]);
  const [loading, setLoading] = useState(false);
  
  // Map modal state for exact location clues
  const [mapModalVisible, setMapModalVisible] = useState(false);
  const [mapLocations, setMapLocations] = useState<Array<{
    latitude: number;
    longitude: number;
    teamName?: string;
  }>>([]);

  // Ensure seekers share their location while game is active (required for distance-based clues)
  useLocationTracker({
    teamId: currentTeam.id,
    gameId: game.id,
    enabled: currentTeam.role === 'seeker',
    isActive: game.status === 'active',
  });

  useEffect(() => {
    loadClueTypes();
    loadClueHistory();
    
    // Set up periodic refresh for clue history as fallback (every 30 seconds)
    const intervalId = setInterval(() => {
      if (currentTeam.role === 'seeker') {
        console.log('Periodic clue history refresh');
        loadClueHistory();
      }
    }, 30000); // 30 seconds
    
    return () => clearInterval(intervalId);
  }, [game.id]);

  // Seeker-side WebSocket to refresh on clue responses with auto-reconnect + heartbeat
  const wsUrl = useMemo(() => getWebsocketUrl(), []);
  useGameWebSocket({
    wsUrl,
    gameId: currentTeam.role === 'seeker' ? game.id : '',
    onMessage: (msg) => {
      if (currentTeam.role !== 'seeker') return;
      
      // Refresh clue history on clue responses directed to this team
      if (msg?.type === 'clueResponse' && msg?.requestingTeamId === currentTeam.id) {
        console.log('Received clue response, refreshing clue history');
        loadClueHistory();
      }
      
      // Also refresh on general game updates to catch timeout clues and other scenarios
      if (msg?.type === 'gameUpdate') {
        console.log('Received game update, refreshing clue history');
        loadClueHistory();
      }
    },
  });

  const loadClueTypes = async () => {
    try {
      const types = await ApiService.getClueTypes();
      setClueTypes(types);
    } catch (error) {
      console.error('Failed to load clue types:', error);
    }
  };

  const loadClueHistory = async () => {
    try {
      const history = await ApiService.getClueHistory(game.id, currentTeam.id);
  // Ensure newest clues appear first in the list
  const sorted = (history || []).slice().sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
  setPurchasedClues(sorted);
    } catch (error) {
  console.error('Failed to load clue history:', error);
  const message = (error as any)?.message || 'Failed to fetch clue history';
  Alert.alert('Clue History', message);
    }
  };

  const getStatusMessage = () => {
    if (game.status === 'waiting') {
      return 'Game has not started yet. Clues will be available once the game begins.';
    } else if (game.status === 'paused') {
      return 'Game is paused. All clue purchases are temporarily disabled.';
    } else if (game.status === 'ended') {
      return 'Game has ended. No more clues can be purchased.';
    }
    return null;
  };

  const canPurchaseClues = () => {
    return game.status === 'active';
  };

  const purchaseClue = async (clueType: ClueType) => {
    if (!canPurchaseClues()) {
      Alert.alert('Cannot Purchase', 'Clues can only be purchased when the game is active.');
      return;
    }
    
    if (currentTeam.tokens < clueType.cost) {
      Alert.alert('Insufficient Tokens', `You need ${clueType.cost} tokens to purchase this clue.`);
      return;
    }

    Alert.alert(
      'Purchase Clue',
      `Purchase "${clueType.name}" for ${clueType.cost} tokens?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Purchase',
          onPress: async () => {
            setLoading(true);
            try {
              // Ensure seeker has a fresh server-side location before purchasing
              if (currentTeam.role === 'seeker') {
                try {
                  const { status } = await ExpoLocation.requestForegroundPermissionsAsync();
                  if (status !== 'granted') {
                    throw new Error('Location permission not granted');
                  }
                  const deviceLoc = await ExpoLocation.getCurrentPositionAsync({ accuracy: ExpoLocation.Accuracy.High });
                  await ApiService.updateLocation(
                    currentTeam.id,
                    deviceLoc.coords.latitude,
                    deviceLoc.coords.longitude,
                    game.id
                  );
                } catch (locErr) {
                  setLoading(false);
                  Alert.alert(
                    'Location Required',
                    'We need your current location to target the closest hider for this clue. Please enable location permissions and try again.'
                  );
                  return;
                }
              }
              const result = await ApiService.purchaseClue(
                clueType.id,
                game.id,
                currentTeam.id,
                clueType.description
              );
              
              // Automatically show map for exact location clues
              if (clueType.id === 'exact-location') {
                if (result.locations && result.locations.length > 0) {
                  // Use new locations array format
                  const locations = result.locations.map((loc: any) => ({
                    latitude: loc.latitude,
                    longitude: loc.longitude,
                    teamName: loc.teamName,
                  }));
                  setMapLocations(locations);
                  setMapModalVisible(true);
                  loadClueHistory();
                } else if (result.hiderData && result.hiderData.length > 0) {
                  // Backward compatibility with hiderData format
                  const locations = result.hiderData
                    .filter((hider: any) => hider.latitude && hider.longitude)
                    .map((hider: any) => ({
                      latitude: hider.latitude!,
                      longitude: hider.longitude!,
                      teamName: hider.teamName,
                    }));
                  
                  if (locations.length > 0) {
                    setMapLocations(locations);
                    setMapModalVisible(true);
                    loadClueHistory();
                  }
                } else if (result.location) {
                  // Legacy single location format
                  setMapLocations([{
                    latitude: result.location.latitude,
                    longitude: result.location.longitude,
                    teamName: result.location.teamName,
                  }]);
                  setMapModalVisible(true);
                  loadClueHistory();
                } else {
                  // Fallback to alert if no location data found
                  Alert.alert('Success', result.message || 'Clue purchased successfully!');
                  loadClueHistory();
                }
              } else {
                // Show regular alert for other clue types
                let alertText = result.text;
                
                // For multi-hider clues, enhance the alert text
                if (result.hiderData && result.hiderData.length > 1) {
                  alertText += `\n\nThis clue covers ${result.hiderData.length} hider teams. Check your clue history for detailed information.`;
                }
                
                Alert.alert(
                  'Clue Purchased!',
                  alertText,
                  [
                    {
                      text: 'OK',
                      onPress: () => {
                        loadClueHistory();
                      }
                    }
                  ]
                );
              }
              
              // Immediate refresh after successful purchase + delayed refresh as fallback
              loadClueHistory();
              setTimeout(() => {
                console.log('Delayed clue history refresh after purchase');
                loadClueHistory();
              }, 2000); // 2 second delay to allow for server processing
              
            } catch (error: any) {
              const message = error?.message || 'Failed to purchase clue. Please try again.';
              Alert.alert('Error', message);
              console.error('Failed to purchase clue:', error);
            } finally {
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const renderClueType = ({ item }: { item: ClueType }) => {
    const canAfford = currentTeam.tokens >= item.cost;
    const gameActive = canPurchaseClues();
    const isDisabled = loading || !canAfford || !gameActive;

    return (
      <TouchableOpacity
        style={[styles.clueCard, isDisabled && styles.unaffordableCard]}
        onPress={() => purchaseClue(item)}
        disabled={isDisabled}
      >
        <View style={styles.clueHeader}>
          <Text style={styles.clueName}>{item.name}</Text>
          <View style={[styles.costBadge, isDisabled && styles.unaffordableBadge]}>
            <Text style={[styles.costText, isDisabled && styles.unaffordableCostText]}>
              {item.cost} 🪙
            </Text>
          </View>
        </View>
        <Text style={styles.clueDescription}>{item.description}</Text>
        {item.range && (
          <Text style={styles.rangeText}>
            Range: {item.range}m from your location
          </Text>
        )}
        {!item.range && (
          <Text style={styles.rangeText}>
            Range: Unlimited (targets all hiders)
          </Text>
        )}
        {!canAfford && gameActive && (
          <Text style={styles.insufficientText}>
            Need {item.cost - currentTeam.tokens} more tokens
          </Text>
        )}
        {!gameActive && (
          <Text style={styles.insufficientText}>
            Game must be active to purchase clues
          </Text>
        )}
      </TouchableOpacity>
    );
  };

  const [visibleClueIds, setVisibleClueIds] = useState<Set<string>>(new Set());
  const [visibleMultiHiderImages, setVisibleMultiHiderImages] = useState<Set<string>>(new Set());
  const [clueNavigationIndices, setClueNavigationIndices] = useState<Map<string, number>>(new Map());

  const viewabilityConfig = useRef({ itemVisiblePercentThreshold: 50 }).current;
  const onViewableItemsChanged = useRef(({ viewableItems }: { viewableItems: any[] }) => {
    const ids = new Set(viewableItems.map((v) => v.item?.id).filter(Boolean));
    setVisibleClueIds(ids);
    setVisibleMultiHiderImages(ids); // Also update multi-hider image visibility
  }).current;

  // Component for displaying multi-hider clues with swiping
  const MultiHiderClueDisplay: React.FC<{ 
    clue: Clue; 
    hiderData: HiderClueData[]; 
    shouldRenderImages: boolean; 
  }> = ({ clue, hiderData, shouldRenderImages }) => {
    const screenWidth = Dimensions.get('window').width;
    
    // Use persistent navigation state that survives re-renders
    const rawCurrentIndex = clueNavigationIndices.get(clue.id) || 0;
    const currentIndex = Math.min(rawCurrentIndex, hiderData.length - 1); // Bounds check
    const setCurrentIndex = (index: number) => {
      const boundedIndex = Math.max(0, Math.min(index, hiderData.length - 1));
      const newMap = new Map(clueNavigationIndices);
      newMap.set(clue.id, boundedIndex);
      setClueNavigationIndices(newMap);
    };
    
    const currentHider = hiderData[currentIndex];
    const isExactLocation = clue.clueTypeId === 'exact-location';
    const isSelfieClue = clue.clueTypeId === 'selfie';

    const toggleImageVisibility = () => {
      const newSet = new Set(visibleMultiHiderImages);
      if (newSet.has(clue.id)) {
        newSet.delete(clue.id);
      } else {
        newSet.add(clue.id);
      }
      setVisibleMultiHiderImages(newSet);
    };

    const shouldShowImage = isSelfieClue && 
                           currentHider.additionalData && 
                           currentHider.additionalData.includes('/api/uploads/') &&
                           visibleMultiHiderImages.has(clue.id);
    
    const handlePrevious = () => {
      const newIndex = currentIndex > 0 ? currentIndex - 1 : hiderData.length - 1;
      setCurrentIndex(newIndex);
    };
    
    const handleNext = () => {
      const newIndex = currentIndex < hiderData.length - 1 ? currentIndex + 1 : 0;
      setCurrentIndex(newIndex);
    };
    
    const handleLocationPress = () => {
      if (isExactLocation) {
        // Show all locations on the map, not just the current one
        const locations = hiderData
          .filter(hider => hider.latitude && hider.longitude)
          .map(hider => ({
            latitude: hider.latitude!,
            longitude: hider.longitude!,
            teamName: hider.teamName,
          }));
        
        if (locations.length > 0) {
          setMapLocations(locations);
          setMapModalVisible(true);
        }
      }
    };
    
    return (
      <View style={styles.multiHiderContainer}>
        {/* Navigation Header */}
        <View style={styles.navigationHeader}>
          <TouchableOpacity 
            onPress={handlePrevious} 
            style={styles.navButton}
            disabled={hiderData.length <= 1}
          >
            <Text style={[styles.navButtonText, hiderData.length <= 1 && styles.disabledNav]}>‹</Text>
          </TouchableOpacity>
          
          <View style={styles.hiderInfo}>
            <Text style={styles.hiderName}>{currentHider.teamName}</Text>
            <Text style={styles.hiderCounter}>
              {currentIndex + 1} of {hiderData.length}
            </Text>
          </View>
          
          <TouchableOpacity 
            onPress={handleNext} 
            style={styles.navButton}
            disabled={hiderData.length <= 1}
          >
            <Text style={[styles.navButtonText, hiderData.length <= 1 && styles.disabledNav]}>›</Text>
          </TouchableOpacity>
        </View>
        
        {/* Clue Content */}
        <View style={styles.hiderClueContent}>
          {isExactLocation && currentHider.latitude && currentHider.longitude ? (
            <TouchableOpacity onPress={handleLocationPress}>
              <Text style={styles.coordinatesText}>
                {currentHider.latitude.toFixed(6)}, {currentHider.longitude.toFixed(6)}
              </Text>
              {currentHider.distance && (
                <Text style={styles.distanceText}>
                  Distance: {currentHider.distance.toFixed(0)}m away
                </Text>
              )}
              <Text style={styles.mapHint}>📍 Tap to view on map</Text>
            </TouchableOpacity>
          ) : isSelfieClue && currentHider.additionalData && currentHider.additionalData.includes('/api/uploads/') ? (
            // Selfie image
            shouldShowImage ? (
              <TouchableOpacity onPress={toggleImageVisibility}>
                <Image
                  source={{ uri: currentHider.additionalData }}
                  style={styles.selfieImage}
                  resizeMode="cover"
                />
                <Text style={styles.tapToHideText}>Tap to hide</Text>
              </TouchableOpacity>
            ) : (
              <TouchableOpacity onPress={toggleImageVisibility} style={styles.imagePlaceholder}>
                <Text style={styles.placeholderText}>Selfie from {currentHider.teamName}</Text>
                <Text style={styles.placeholderText}>Tap to view</Text>
              </TouchableOpacity>
            )
          ) : (
            // Regular text clue data
            <Text style={styles.hiderClueText}>
              {currentHider.additionalData || 'No additional information'}
            </Text>
          )}
          
          {/* Additional info for distance/direction clues */}
          {currentHider.distance && !isExactLocation && (
            <Text style={styles.distanceText}>
              Distance: {currentHider.distance.toFixed(0)}m
            </Text>
          )}
          {currentHider.direction && (
            <Text style={styles.directionText}>
              Direction: {currentHider.direction}
            </Text>
          )}
        </View>
      </View>
    );
  };

  const renderPurchasedClue = ({ item }: { item: Clue }) => {
    const timeAgo = new Date(item.timestamp).toLocaleTimeString();
    const isImage = typeof item.text === 'string' && /\/api\/uploads\/files\//.test(item.text);
    const shouldRenderImage = isImage && visibleClueIds.has(item.id);
    
    // Check for multi-hider clue data
    const hasMultiHiderData = item.hiderData && item.hiderData.length > 0;
    const isExactLocationClue = item.clueTypeId === 'exact-location';
    const isSelfieClue = item.clueTypeId === 'selfie';
    
    // Legacy single location clue support
    const hasLegacyLocation = item.location && !hasMultiHiderData;
    
    const getClueTypeName = () => {
      if (isExactLocationClue) return 'Exact Location Clue';
      if (isSelfieClue) return 'Selfie Clue';
      if (item.clueTypeId === 'closest-building') return 'Landmark Clue';
      if (item.clueTypeId === 'relative-direction') return 'Direction Clue';
      if (item.clueTypeId === 'distance-from-seekers') return 'Distance Clue';
      return 'Purchased Clue';
    };
    
    const handleLegacyLocationPress = () => {
      if (item.location) {
        setMapLocations([{
          latitude: item.location.latitude,
          longitude: item.location.longitude,
          teamName: item.location.teamName,
        }]);
        setMapModalVisible(true);
      }
    };

    return (
      <TouchableOpacity
        style={[
          styles.purchasedClueCard, 
          isExactLocationClue && styles.locationClueCard,
          hasMultiHiderData && styles.multiHiderClueCard
        ]}
        onPress={hasLegacyLocation ? handleLegacyLocationPress : undefined}
        disabled={!hasLegacyLocation}
      >
        <View style={styles.purchasedClueHeader}>
          <Text style={styles.purchasedClueName}>
            {getClueTypeName()}
          </Text>
          <Text style={styles.purchasedClueTime}>{timeAgo}</Text>
        </View>
        
        {/* Main clue text */}
        <Text style={styles.purchasedClueContent}>{item.text}</Text>
        
        {/* Multi-hider data display */}
        {hasMultiHiderData && (
          <MultiHiderClueDisplay 
            clue={item} 
            hiderData={item.hiderData!} 
            shouldRenderImages={shouldRenderImage}
          />
        )}
        
        {/* Legacy single location display */}
        {hasLegacyLocation && (
          <View>
            <Text style={styles.coordinatesText}>
              {item.location!.latitude.toFixed(6)}, {item.location!.longitude.toFixed(6)}
            </Text>
            <Text style={styles.mapHint}>📍 Tap to view on map</Text>
          </View>
        )}
        
        {/* Legacy single image display */}
        {isImage && !hasMultiHiderData && (
          shouldRenderImage ? (
            <Image
              source={{ uri: item.text }}
              style={{ width: '100%', height: 240, borderRadius: 8, marginTop: 8 }}
              resizeMode="cover"
            />
          ) : (
            <View style={{ width: '100%', height: 120, borderRadius: 8, marginTop: 8, backgroundColor: '#eee', alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ color: '#666' }}>Image (tap to view)</Text>
            </View>
          )
        )}
        
        <Text style={styles.purchasedClueCost}>Cost: {item.cost} tokens</Text>
      </TouchableOpacity>
    );
  };

  const hiderTeams = game.teams.filter((t: Team) => t.role === 'hider');

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Buy Clues</Text>
          <Text style={styles.subtitle}>
            Spend tokens to get hints about hider locations
          </Text>
        </View>

        <View style={styles.tokenSection}>
          <Text style={styles.tokenTitle}>Available Tokens</Text>
          <Text style={styles.tokenValue}>{currentTeam.tokens}</Text>
        </View>

        <View style={styles.gameInfo}>
          <Text style={styles.gameInfoTitle}>Active Hiders</Text>
          {hiderTeams.map((team, index) => (
            <Text key={team.id} style={styles.hiderName}>
              • {team.name}
            </Text>
          ))}
          <Text style={styles.gameInfoNote}>
            Clues target the closest hider to your current location
          </Text>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Available Clues</Text>
          
          {getStatusMessage() && (
            <View style={styles.statusMessageContainer}>
              <Text style={styles.statusMessageText}>{getStatusMessage()}</Text>
            </View>
          )}
          
          <FlatList
            data={clueTypes}
            renderItem={renderClueType}
            keyExtractor={(item) => item.id}
            scrollEnabled={false}
            showsVerticalScrollIndicator={false}
          />
        </View>

        {purchasedClues.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Purchased Clues</Text>
            <FlatList
              data={purchasedClues}
              renderItem={renderPurchasedClue}
              keyExtractor={(item) => item.id}
              scrollEnabled={false}
              showsVerticalScrollIndicator={false}
              initialNumToRender={4}
              windowSize={5}
              onViewableItemsChanged={onViewableItemsChanged}
              viewabilityConfig={viewabilityConfig}
            />
          </View>
        )}

        {purchasedClues.length === 0 && (
          <View style={styles.emptyState}>
            <Text style={styles.emptyStateText}>
              No clues purchased yet. Complete challenges to earn tokens!
            </Text>
          </View>
        )}
      </ScrollView>
      
      {/* Map Modal for exact location clues */}
      {mapLocations.length > 0 && (
        <MapModal
          visible={mapModalVisible}
          onClose={() => {
            setMapModalVisible(false);
            setMapLocations([]);
          }}
          locations={mapLocations}
        />
      )}
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
  tokenSection: {
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
  tokenTitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 8,
  },
  tokenValue: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#003366',
  },
  gameInfo: {
    backgroundColor: '#e8f4fd',
    padding: 16,
    borderRadius: 8,
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#003366',
  },
  gameInfoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 8,
  },
  hiderName: {
    fontSize: 14,
    color: '#333',
    marginBottom: 4,
  },
  gameInfoNote: {
    fontSize: 12,
    color: '#666',
    marginTop: 8,
    fontStyle: 'italic',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 16,
  },
  clueCard: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: '#003366',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  unaffordableCard: {
    borderColor: '#ccc',
    opacity: 0.6,
  },
  clueHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  clueName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    flex: 1,
  },
  costBadge: {
    backgroundColor: '#003366',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  unaffordableBadge: {
    backgroundColor: '#ccc',
  },
  costText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 14,
  },
  unaffordableCostText: {
    color: '#666',
  },
  clueDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  rangeText: {
    fontSize: 12,
    color: '#007acc',
    marginTop: 4,
    fontStyle: 'italic',
  },
  insufficientText: {
    fontSize: 12,
    color: '#e74c3c',
    marginTop: 8,
    fontStyle: 'italic',
  },
  purchasedClueCard: {
    backgroundColor: '#e8f5e8',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#27ae60',
  },
  multiHiderClueCard: {
    backgroundColor: '#f0f8ff',
    borderLeftColor: '#007acc',
  },
  purchasedClueHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  purchasedClueName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#27ae60',
  },
  purchasedClueTime: {
    fontSize: 12,
    color: '#666',
  },
  purchasedClueContent: {
    fontSize: 16,
    color: '#333',
    marginBottom: 8,
    lineHeight: 22,
  },
  purchasedClueCost: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
  },
  emptyState: {
    backgroundColor: '#fff',
    padding: 40,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 24,
  },
  emptyStateText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 22,
  },
  statusMessageContainer: {
    backgroundColor: '#fff3cd',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#f39c12',
  },
  statusMessageText: {
    fontSize: 16,
    color: '#856404',
    textAlign: 'center',
    fontWeight: '600',
  },
  locationClueCard: {
    backgroundColor: '#e3f2fd',
    borderLeftColor: '#2196f3',
  },
  coordinatesText: {
    fontSize: 12,
    fontFamily: 'monospace',
    color: '#555',
    marginTop: 4,
    marginBottom: 4,
  },
  mapHint: {
    fontSize: 12,
    color: '#2196f3',
    fontStyle: 'italic',
    marginTop: 4,
  },
  // Multi-hider clue styles
  multiHiderContainer: {
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
    overflow: 'hidden',
    marginTop: 8,
  },
  navigationHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#e9ecef',
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  navButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
    elevation: 2,
  },
  navButtonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
  },
  disabledNav: {
    color: '#ccc',
  },
  hiderInfo: {
    alignItems: 'center',
    flex: 1,
  },
  hiderCounter: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  hiderClueContent: {
    padding: 16,
  },
  hiderClueText: {
    fontSize: 15,
    color: '#333',
    lineHeight: 20,
  },
  distanceText: {
    fontSize: 13,
    color: '#666',
    marginTop: 4,
  },
  directionText: {
    fontSize: 13,
    color: '#666',
    marginTop: 2,
  },
  selfieImage: {
    width: '100%',
    height: 240,
    borderRadius: 8,
    marginBottom: 8,
  },
  imagePlaceholder: {
    width: '100%',
    height: 120,
    backgroundColor: '#eee',
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  placeholderText: {
    color: '#666',
    fontSize: 14,
  },
  tapToHideText: {
    color: '#666',
    fontSize: 12,
    textAlign: 'center',
    marginTop: 4,
  },
});

export default CluesTab;
