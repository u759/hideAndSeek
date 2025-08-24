import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  TextInput,
  Alert,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList } from '../types';
import ApiService from '../services/api';

type GameSetupScreenNavigationProp = StackNavigationProp<RootStackParamList, 'GameSetup'>;

const GameSetupScreen: React.FC = () => {
  const navigation = useNavigation<GameSetupScreenNavigationProp>();
  const [teamNames, setTeamNames] = useState<string[]>(['', '']);
  const [roundLengthMinutes, setRoundLengthMinutes] = useState<string>('');
  const [loading, setLoading] = useState(false);

  const addTeam = () => {
    if (teamNames.length < 6) {
      setTeamNames([...teamNames, '']);
    }
  };

  const removeTeam = (index: number) => {
    if (teamNames.length > 2) {
      const newTeams = teamNames.filter((_, i) => i !== index);
      setTeamNames(newTeams);
    }
  };

  const updateTeamName = (index: number, name: string) => {
    const newTeams = [...teamNames];
    newTeams[index] = name;
    setTeamNames(newTeams);
  };

  const createGame = async () => {
    const validTeams = teamNames.filter(name => name.trim() !== '');
    
    if (validTeams.length < 2) {
      Alert.alert('Error', 'Please enter at least 2 team names');
      return;
    }

    // Validate round length if provided
    let roundLength: number | undefined;
    if (roundLengthMinutes.trim()) {
      const parsedLength = parseInt(roundLengthMinutes, 10);
      if (isNaN(parsedLength) || parsedLength <= 0) {
        Alert.alert('Error', 'Round duration must be a positive number');
        return;
      }
      if (parsedLength > 999) {
        Alert.alert('Error', 'Round duration cannot exceed 999 minutes');
        return;
      }
      roundLength = parsedLength;
    }

    setLoading(true);
    try {
      const game = await ApiService.createGame(validTeams, roundLength);
      
      // Navigate to game lobby where players can join
      navigation.navigate('GameLobby', { 
        gameId: game.id, 
        gameCode: game.code 
      });
    } catch (error) {
      Alert.alert('Error', 'Failed to create game. Please try again.');
      console.error('Failed to create game:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Set Up Your Game</Text>
          <Text style={styles.subtitle}>
            Enter team names (2-6 teams). The first team will be the Seekers, all others will be Hiders.
          </Text>
        </View>

        <View style={styles.teamsContainer}>
          {teamNames.map((name, index) => (
            <View key={index} style={styles.teamRow}>
              <Text style={styles.teamLabel}>
                Team {index + 1} {index === 0 ? '(Seekers)' : '(Hiders)'}:
              </Text>
              <View style={styles.inputRow}>
                <TextInput
                  style={styles.teamInput}
                  value={name}
                  onChangeText={(text) => updateTeamName(index, text)}
                  placeholder={`Enter team ${index + 1} name`}
                  maxLength={30}
                />
                {teamNames.length > 2 && (
                  <TouchableOpacity
                    style={styles.removeButton}
                    onPress={() => removeTeam(index)}
                  >
                    <Text style={styles.removeButtonText}>✕</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          ))}
        </View>

        <View style={styles.actions}>
          {teamNames.length < 6 && (
            <TouchableOpacity style={styles.addButton} onPress={addTeam}>
              <Text style={styles.addButtonText}>+ Add Team</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity
            style={[styles.createButton, loading && styles.disabledButton]}
            onPress={createGame}
            disabled={loading}
          >
            <Text style={styles.createButtonText}>
              {loading ? 'Creating Game...' : 'Create Game'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.roundLengthContainer}>
          <Text style={styles.roundLengthLabel}>Round Duration Limit (optional):</Text>
          <TextInput
            style={styles.roundLengthInput}
            value={roundLengthMinutes}
            onChangeText={(text) => {
              // Only allow numbers
              const numericText = text.replace(/[^0-9]/g, '');
              setRoundLengthMinutes(numericText);
            }}
            placeholder="Enter minutes per round (e.g., 30)"
            keyboardType="numeric"
            maxLength={3}
          />
          <Text style={styles.roundLengthHint}>
            • Enter maximum duration per round in minutes (numbers only)
          </Text>
          <Text style={styles.roundLengthHint}>
            • Each round will auto-pause when time limit is reached
          </Text>
          <Text style={styles.roundLengthHint}>
            • Leave empty for unlimited round duration
          </Text>
        </View>

        <View style={styles.instructions}>
          <Text style={styles.instructionsTitle}>Game Rules:</Text>
          <Text style={styles.instructionsText}>
            • Hiders get a 10-minute head start to hide on UBC campus
          </Text>
          <Text style={styles.instructionsText}>
            • Seekers draw challenge cards to earn tokens
          </Text>
          <Text style={styles.instructionsText}>
            • Use tokens to buy clues about hider locations
          </Text>
          <Text style={styles.instructionsText}>
            • Roles rotate when all teams are found
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
    padding: 20,
  },
  header: {
    marginBottom: 30,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#003366',
    textAlign: 'center',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 22,
  },
  teamsContainer: {
    marginBottom: 30,
  },
  teamRow: {
    marginBottom: 20,
  },
  teamLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#003366',
    marginBottom: 8,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  teamInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 16,
    backgroundColor: '#fff',
  },
  removeButton: {
    marginLeft: 10,
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#ff6b6b',
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  actions: {
    marginBottom: 30,
  },
  addButton: {
    borderWidth: 2,
    borderColor: '#003366',
    borderStyle: 'dashed',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 20,
  },
  addButtonText: {
    color: '#003366',
    fontSize: 16,
    fontWeight: '600',
  },
  createButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  createButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  instructions: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    marginBottom: 20,
  },
  instructionsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 10,
  },
  instructionsText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 5,
  },
  roundLengthContainer: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    marginBottom: 20,
  },
  roundLengthLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#003366',
    marginBottom: 10,
  },
  roundLengthInput: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 16,
    backgroundColor: '#fff',
    marginBottom: 8,
  },
  roundLengthHint: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
  },
});

export default GameSetupScreen;
