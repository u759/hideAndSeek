import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  Alert,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { useNavigation, useRoute, RouteProp, useIsFocused } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList, Game, Team } from '../types';
import ApiService from '../services/api';
import useGameState from '../hooks/useGameState';

type TeamJoinScreenRouteProp = RouteProp<RootStackParamList, 'TeamJoin'>;
type TeamJoinScreenNavigationProp = StackNavigationProp<RootStackParamList, 'TeamJoin'>;

const TeamJoinScreen: React.FC = () => {
  const navigation = useNavigation<TeamJoinScreenNavigationProp>();
  const route = useRoute<TeamJoinScreenRouteProp>();
  const { gameId, gameCode } = route.params;
  
  const [joining, setJoining] = useState(false);
  const isFocused = useIsFocused();

  // Use centralized game state management with WebSocket updates
  const { game, loading, error, connected, refresh } = useGameState({
    gameId,
    enabled: isFocused, // Only connect when screen is focused
  });

  // Handle errors (e.g., invalid game code)
  useEffect(() => {
    if (error) {
      Alert.alert('Error', 'Failed to load game. Please check the game code.');
      console.error('Failed to load game:', error);
      navigation.goBack();
    }
  }, [error, navigation]);

  const joinTeam = async (team: Team) => {
    setJoining(true);
    try {
      // Navigate to the game screen with the selected team
      navigation.navigate('Game', { 
        gameId: game!.id, 
        teamId: team.id 
      });
    } catch (error) {
      Alert.alert('Error', 'Failed to join team. Please try again.');
      console.error('Failed to join team:', error);
    } finally {
      setJoining(false);
    }
  };

  if (loading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#003366" />
          <Text style={styles.loadingText}>Loading game...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (!game) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Game not found</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Select Your Team</Text>
          <Text style={styles.subtitle}>
            Game Code: <Text style={styles.gameCode}>{gameCode}</Text>
          </Text>
        </View>

        <View style={styles.teamsSection}>
          <Text style={styles.sectionTitle}>Available Teams:</Text>
          
          {game.teams.map((team, index) => (
            <TouchableOpacity
              key={team.id}
              style={[
                styles.teamCard,
                team.role === 'seeker' ? styles.seekerCard : styles.hiderCard,
              ]}
              onPress={() => joinTeam(team)}
              disabled={joining}
            >
              <View style={styles.teamHeader}>
                <Text style={styles.teamName}>{team.name}</Text>
                <Text style={styles.teamRole}>
                  {team.role === 'seeker' ? '🔍 Seeker' : '👤 Hider'}
                </Text>
              </View>
              
              <Text style={styles.teamDescription}>
                {team.role === 'seeker' 
                  ? 'Draw challenge cards, earn tokens, buy clues to find hiders'
                  : 'Hide around UBC campus, share location automatically'
                }
              </Text>
              
              <View style={styles.teamStats}>
                <Text style={styles.teamStat}>Tokens: {team.tokens}</Text>
                <Text style={styles.teamStat}>
                  Challenges: {team.completedChallenges.length}
                </Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.gameInfo}>
          <Text style={styles.gameInfoTitle}>Game Status</Text>
          <Text style={styles.gameInfoText}>Status: {game.status}</Text>
          <Text style={styles.gameInfoText}>Round: {game.round}</Text>
          <Text style={styles.gameInfoText}>Teams: {game.teams.length}</Text>
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
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 10,
    fontSize: 16,
    color: '#666',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  errorText: {
    fontSize: 18,
    color: '#FF6B6B',
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
  },
  gameCode: {
    fontWeight: 'bold',
    color: '#003366',
    fontFamily: 'monospace',
  },
  teamsSection: {
    marginBottom: 30,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 15,
  },
  teamCard: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    marginBottom: 15,
    borderWidth: 2,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  seekerCard: {
    borderColor: '#2196F3',
  },
  hiderCard: {
    borderColor: '#FF9800',
  },
  teamHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  teamName: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
  },
  teamRole: {
    fontSize: 16,
    fontWeight: '600',
    color: '#666',
  },
  teamDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 15,
  },
  teamStats: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  teamStat: {
    fontSize: 14,
    color: '#999',
  },
  gameInfo: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    marginTop: 20,
  },
  gameInfoTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 10,
  },
  gameInfoText: {
    fontSize: 16,
    color: '#666',
    marginBottom: 5,
  },
});

export default TeamJoinScreen;
