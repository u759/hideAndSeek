import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  Alert,
  RefreshControl,
} from 'react-native';
import { Game, Team } from '../types';
import ApiService from '../services/api';

interface FindHidersTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const FindHidersTab: React.FC<FindHidersTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [loading, setLoading] = useState(false);
  const [gameStats, setGameStats] = useState<any>(null);

  useEffect(() => {
    loadGameStats();
  }, [game.id]);

  const loadGameStats = async () => {
    try {
      const stats = await ApiService.getGameStats(game.id);
      setGameStats(stats);
    } catch (error) {
      console.error('Failed to load game stats:', error);
    }
  };

  const markHiderFound = async (hiderId: string, hiderName: string) => {
    Alert.alert(
      'Hider Found!',
      `Are you sure you found ${hiderName}? They will become a seeker and help find the remaining hiders.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Yes, Found Them!',
          onPress: async () => {
            setLoading(true);
            try {
              const result = await ApiService.markHiderFound(game.id, hiderId, currentTeam.id);
              
              Alert.alert(
                'Hider Found!',
                result.message,
                [
                  {
                    text: 'OK',
                    onPress: () => {
                      onRefresh();
                      loadGameStats();
                    }
                  }
                ]
              );

              if (result.allHidersFound) {
                // Additional alert for round completion
                setTimeout(() => {
                  Alert.alert(
                    'Round Complete!',
                    `All hiders have been found! The game is now paused. You can set up roles for the next round.`,
                    [{ text: 'OK' }]
                  );
                }, 500);
              }
            } catch (error) {
              Alert.alert('Error', 'Failed to mark hider as found.');
              console.error('Failed to mark hider found:', error);
            } finally {
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const renderHider = (hider: Team) => (
    <View key={hider.id} style={styles.hiderCard}>
      <View style={styles.hiderInfo}>
        <Text style={styles.hiderName}>{hider.name}</Text>
        <Text style={styles.hiderStatus}>
          {hider.location 
            ? `Last seen: ${new Date(hider.location.timestamp).toLocaleTimeString()}`
            : 'Location unknown'
          }
        </Text>
      </View>
      <TouchableOpacity
        style={[styles.foundButton, loading && styles.disabledButton]}
        onPress={() => markHiderFound(hider.id, hider.name)}
        disabled={loading}
      >
        <Text style={styles.foundButtonText}>Found!</Text>
      </TouchableOpacity>
    </View>
  );

  const hiders = game.teams.filter(team => team.role === 'hider');
  const seekers = game.teams.filter(team => team.role === 'seeker');

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView 
        style={styles.content}
        refreshControl={
          <RefreshControl refreshing={loading} onRefresh={() => {
            onRefresh();
            loadGameStats();
          }} />
        }
      >
        <View style={styles.header}>
          <Text style={styles.title}>Find the Hiders</Text>
          <Text style={styles.subtitle}>
            Mark hiders as found to convert them to seekers
          </Text>
        </View>

        <View style={styles.statsSection}>
          <View style={styles.statCard}>
            <Text style={styles.statNumber}>{seekers.length}</Text>
            <Text style={styles.statLabel}>Seekers</Text>
          </View>
          <View style={styles.statCard}>
            <Text style={styles.statNumber}>{hiders.length}</Text>
            <Text style={styles.statLabel}>Hiders Remaining</Text>
          </View>
        </View>

        {game.status === 'ended' && (
          <View style={styles.gameEndedCard}>
            <Text style={styles.gameEndedTitle}>🎉 Game Over!</Text>
            <Text style={styles.gameEndedText}>
              {hiders.length === 1 
                ? `${hiders[0].name} is the last hider standing!`
                : 'All hiders have been found!'
              }
            </Text>
          </View>
        )}

        <View style={styles.hidersSection}>
          <Text style={styles.sectionTitle}>
            Remaining Hiders ({hiders.length})
          </Text>
          
          {hiders.length === 0 ? (
            <View style={styles.noHidersCard}>
              <Text style={styles.noHidersText}>
                🎯 All hiders have been found!
              </Text>
            </View>
          ) : (
            hiders.map(renderHider)
          )}
        </View>

        <View style={styles.seekersSection}>
          <Text style={styles.sectionTitle}>
            Current Seekers ({seekers.length})
          </Text>
          {seekers.map(seeker => (
            <View key={seeker.id} style={styles.seekerCard}>
              <Text style={styles.seekerName}>
                {seeker.name} {seeker.id === currentTeam.id ? '(You)' : ''}
              </Text>
              <Text style={styles.seekerTokens}>
                {seeker.tokens} tokens
              </Text>
            </View>
          ))}
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
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
  },
  statsSection: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 24,
  },
  statCard: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    alignItems: 'center',
    minWidth: 100,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statNumber: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#003366',
  },
  statLabel: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  gameEndedCard: {
    backgroundColor: '#e8f5e8',
    padding: 20,
    borderRadius: 12,
    marginBottom: 24,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#4caf50',
  },
  gameEndedTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#2e7d32',
    marginBottom: 8,
  },
  gameEndedText: {
    fontSize: 16,
    color: '#2e7d32',
    textAlign: 'center',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 12,
  },
  hidersSection: {
    marginBottom: 24,
  },
  hiderCard: {
    backgroundColor: 'white',
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  hiderInfo: {
    flex: 1,
  },
  hiderName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  hiderStatus: {
    fontSize: 14,
    color: '#666',
  },
  foundButton: {
    backgroundColor: '#ff4444',
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  foundButtonText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 14,
  },
  noHidersCard: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    alignItems: 'center',
  },
  noHidersText: {
    fontSize: 16,
    color: '#666',
  },
  seekersSection: {
    marginBottom: 24,
  },
  seekerCard: {
    backgroundColor: 'white',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  seekerName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#003366',
  },
  seekerTokens: {
    fontSize: 14,
    color: '#666',
  },
});

export default FindHidersTab;
