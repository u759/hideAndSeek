import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  TextInput,
  Alert,
  Share,
  Clipboard,
} from 'react-native';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList } from '../types';

type GameLobbyScreenRouteProp = RouteProp<RootStackParamList, 'GameLobby'>;
type GameLobbyScreenNavigationProp = StackNavigationProp<RootStackParamList, 'GameLobby'>;

const GameLobbyScreen: React.FC = () => {
  const navigation = useNavigation<GameLobbyScreenNavigationProp>();
  const route = useRoute<GameLobbyScreenRouteProp>();
  const { gameId, gameCode } = route.params;

  const shareGameCode = async () => {
    try {
      await Share.share({
        message: `Join our UBCeek Hide & Seek game!\n\nGame Code: ${gameCode}\n\nDownload the app and use this code to join!`,
        title: 'UBCeek Game Invitation',
      });
    } catch (error) {
      console.error('Error sharing game code:', error);
    }
  };

  const copyGameCode = () => {
    Clipboard.setString(gameCode);
    Alert.alert('Copied!', 'Game code copied to clipboard');
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Game Created!</Text>
          <Text style={styles.subtitle}>
            Share this code with other players so they can join your game
          </Text>
        </View>

        <View style={styles.codeSection}>
          <Text style={styles.codeLabel}>Game Code</Text>
          <View style={styles.codeContainer}>
            <Text style={styles.gameCode}>{gameCode}</Text>
            <TouchableOpacity style={styles.copyButton} onPress={copyGameCode}>
              <Text style={styles.copyButtonText}>📋 Copy</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.instructionsSection}>
          <Text style={styles.instructionsTitle}>How to join:</Text>
          <Text style={styles.instructionText}>
            1. Each player downloads the UBCeek app
          </Text>
          <Text style={styles.instructionText}>
            2. They tap "Join Existing Game" 
          </Text>
          <Text style={styles.instructionText}>
            3. Enter the game code: <Text style={styles.bold}>{gameCode}</Text>
          </Text>
          <Text style={styles.instructionText}>
            4. Select their team name
          </Text>
        </View>

        <View style={styles.actionsSection}>
          <TouchableOpacity style={styles.shareButton} onPress={shareGameCode}>
            <Text style={styles.shareButtonText}>📤 Share Game Code</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.joinButton}
            onPress={() => navigation.navigate('TeamJoin', { gameId, gameCode })}
          >
            <Text style={styles.joinButtonText}>Join This Game</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.footer}>
          <Text style={styles.footerText}>
            Players can join at any time during the game setup phase
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
  codeSection: {
    alignItems: 'center',
    marginVertical: 30,
  },
  codeLabel: {
    fontSize: 18,
    color: '#333',
    marginBottom: 10,
  },
  codeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#003366',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  gameCode: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#003366',
    fontFamily: 'monospace',
    marginRight: 15,
  },
  copyButton: {
    backgroundColor: '#003366',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
  },
  copyButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
  },
  instructionsSection: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    marginVertical: 20,
  },
  instructionsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 15,
  },
  instructionText: {
    fontSize: 16,
    color: '#666',
    marginBottom: 8,
    lineHeight: 24,
  },
  bold: {
    fontWeight: 'bold',
    color: '#003366',
  },
  actionsSection: {
    gap: 15,
  },
  shareButton: {
    backgroundColor: '#4CAF50',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: 'center',
  },
  shareButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  joinButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: 'center',
  },
  joinButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  footer: {
    alignItems: 'center',
    marginTop: 20,
  },
  footerText: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
  },
});

export default GameLobbyScreen;
