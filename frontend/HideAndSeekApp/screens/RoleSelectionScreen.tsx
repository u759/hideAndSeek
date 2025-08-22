import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useFocusEffect } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList } from '../types';
import ApiService from '../services/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

type RoleSelectionScreenNavigationProp = StackNavigationProp<RootStackParamList, 'RoleSelection'>;

const RoleSelectionScreen: React.FC = () => {
  const navigation = useNavigation<RoleSelectionScreenNavigationProp>();
  const [selectedRole, setSelectedRole] = useState<'seeker' | 'hider' | null>(null);
  const [loading, setLoading] = useState(false);

  const joinGame = async () => {
    if (!selectedRole) {
      Alert.alert('Error', 'Please select a role first');
      return;
    }

    setLoading(true);
    try {
      // Create a single-player game with the selected role
      const teamName = selectedRole === 'seeker' ? 'Seekers' : 'Hiders';
      const game = await ApiService.createGameWithRole([teamName], selectedRole);
      
      // Navigate to game screen
      navigation.navigate('Game', { 
        gameId: game.id, 
        teamId: game.teams[0].id 
      });
    } catch (error) {
      Alert.alert('Error', 'Failed to join game. Please try again.');
      console.error('Failed to join game:', error);
    } finally {
      setLoading(false);
    }
  };

  // Refresh game data when the screen gains focus to avoid stale views
  useFocusEffect(
    useCallback(() => {
      let mounted = true;
      (async () => {
        try {
          const bgGameId = await AsyncStorage.getItem('bg_gameId');
          if (bgGameId) {
            await ApiService.getGame(bgGameId);
          }
        } catch (err) {
          // non-fatal
        }
      })();

      return () => { mounted = false; };
    }, [])
  );

  // Also subscribe to navigation focus explicitly to handle back-gesture cases reliably
  React.useEffect(() => {
    const unsubscribe = navigation.addListener('focus', async () => {
      try {
        const bgGameId = await AsyncStorage.getItem('bg_gameId');
        if (bgGameId) {
          await ApiService.getGame(bgGameId);
        }
      } catch (_) {
        // ignore
      }
    });

    return unsubscribe;
  }, [navigation]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Choose Your Role</Text>
          <Text style={styles.subtitle}>
            Select whether you want to be a Seeker or a Hider
          </Text>
        </View>

        <View style={styles.rolesContainer}>
          <TouchableOpacity
            style={[
              styles.roleCard,
              selectedRole === 'seeker' && styles.selectedCard,
            ]}
            onPress={() => setSelectedRole('seeker')}
          >
            <Text style={[
              styles.roleTitle,
              selectedRole === 'seeker' && styles.selectedText,
            ]}>
              🔍 Seeker
            </Text>
            <Text style={styles.roleDescription}>
              • Draw challenge cards to earn tokens
            </Text>
            <Text style={styles.roleDescription}>
              • Use tokens to buy clues about hider locations
            </Text>
            <Text style={styles.roleDescription}>
              • Try to find all the hiders
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.roleCard,
              selectedRole === 'hider' && styles.selectedCard,
            ]}
            onPress={() => setSelectedRole('hider')}
          >
            <Text style={[
              styles.roleTitle,
              selectedRole === 'hider' && styles.selectedText,
            ]}>
              👤 Hider
            </Text>
            <Text style={styles.roleDescription}>
              • Share your location automatically
            </Text>
            <Text style={styles.roleDescription}>
              • Stay hidden from the seekers
            </Text>
            <Text style={styles.roleDescription}>
              • Try to avoid being found
            </Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity
          style={[
            styles.joinButton,
            !selectedRole && styles.disabledButton,
          ]}
          onPress={joinGame}
          disabled={!selectedRole || loading}
        >
          <Text style={[
            styles.joinButtonText,
            !selectedRole && styles.disabledButtonText,
          ]}>
            {loading ? 'Joining...' : 'Join Game'}
          </Text>
        </TouchableOpacity>
      </View>
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
    justifyContent: 'space-between',
  },
  header: {
    alignItems: 'center',
    marginTop: 40,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 40,
  },
  rolesContainer: {
    flex: 1,
    justifyContent: 'center',
    gap: 20,
  },
  roleCard: {
    backgroundColor: 'white',
    padding: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#e0e0e0',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  selectedCard: {
    borderColor: '#003366',
    backgroundColor: '#f0f8ff',
  },
  roleTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 16,
    textAlign: 'center',
  },
  selectedText: {
    color: '#003366',
  },
  roleDescription: {
    fontSize: 16,
    color: '#666',
    lineHeight: 24,
    marginBottom: 8,
  },
  joinButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 20,
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  joinButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  disabledButtonText: {
    color: '#666',
  },
});

export default RoleSelectionScreen;
