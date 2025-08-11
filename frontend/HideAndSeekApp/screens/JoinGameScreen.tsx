import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  TextInput,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList } from '../types';
import ApiService from '../services/api';

type JoinGameScreenNavigationProp = StackNavigationProp<RootStackParamList, 'JoinGame'>;

const JoinGameScreen: React.FC = () => {
  const navigation = useNavigation<JoinGameScreenNavigationProp>();
  const [gameCode, setGameCode] = useState('');
  const [loading, setLoading] = useState(false);

  const joinGame = async () => {
    if (!gameCode.trim()) {
      Alert.alert('Error', 'Please enter a game code');
      return;
    }

    setLoading(true);
    try {
      // Find game by code
      const game = await ApiService.getGameByCode(gameCode.toUpperCase());
      
      // Navigate to team selection
      navigation.navigate('TeamJoin', { 
        gameId: game.id, 
        gameCode: gameCode.toUpperCase() 
      });
    } catch (error) {
      Alert.alert('Error', 'Game not found. Please check the code and try again.');
      console.error('Failed to find game:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Join Game</Text>
          <Text style={styles.subtitle}>
            Enter the game code shared by the game creator
          </Text>
        </View>

        <View style={styles.inputSection}>
          <Text style={styles.inputLabel}>Game Code</Text>
          <TextInput
            style={styles.codeInput}
            value={gameCode}
            onChangeText={setGameCode}
            placeholder="Enter 6-letter code"
            maxLength={6}
            autoCapitalize="characters"
            autoCorrect={false}
            textAlign="center"
          />
        </View>

        <TouchableOpacity
          style={[
            styles.joinButton,
            !gameCode.trim() && styles.disabledButton,
          ]}
          onPress={joinGame}
          disabled={!gameCode.trim() || loading}
        >
          <Text style={[
            styles.joinButtonText,
            !gameCode.trim() && styles.disabledButtonText,
          ]}>
            {loading ? 'Finding Game...' : 'Join Game'}
          </Text>
        </TouchableOpacity>

        <View style={styles.helpSection}>
          <Text style={styles.helpTitle}>Need help?</Text>
          <Text style={styles.helpText}>
            • Game codes are 6 letters long
          </Text>
          <Text style={styles.helpText}>
            • Ask the game creator to share the code
          </Text>
          <Text style={styles.helpText}>
            • Make sure you're connected to the internet
          </Text>
        </View>
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
    marginTop: 60,
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
  inputSection: {
    alignItems: 'center',
    marginVertical: 40,
  },
  inputLabel: {
    fontSize: 18,
    color: '#333',
    marginBottom: 15,
  },
  codeInput: {
    backgroundColor: 'white',
    borderWidth: 2,
    borderColor: '#003366',
    borderRadius: 12,
    paddingVertical: 20,
    paddingHorizontal: 30,
    fontSize: 24,
    fontFamily: 'monospace',
    textAlign: 'center',
    letterSpacing: 4,
    color: '#003366',
    fontWeight: 'bold',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    minWidth: 200,
  },
  joinButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: 'center',
    marginVertical: 20,
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
  helpSection: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    marginBottom: 40,
  },
  helpTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 15,
  },
  helpText: {
    fontSize: 16,
    color: '#666',
    marginBottom: 8,
    lineHeight: 24,
  },
});

export default JoinGameScreen;
