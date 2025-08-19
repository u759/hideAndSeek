import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Modal,
  TouchableOpacity,
  Alert,
  ScrollView,
} from 'react-native';
import { Game, Team } from '../types';
import ApiService from '../services/api';

interface RoleSelectionModalProps {
  visible: boolean;
  game: Game;
  currentTeam: Team;
  onClose: () => void;
  onRefresh: () => void;
}

const RoleSelectionModal: React.FC<RoleSelectionModalProps> = ({
  visible,
  game,
  currentTeam,
  onClose,
  onRefresh,
}) => {
  const [loading, setLoading] = useState(false);

  const updateRole = async (newRole: 'seeker' | 'hider') => {
    if (newRole === currentTeam.role) {
      Alert.alert('Info', `You are already a ${newRole}`);
      return;
    }

    setLoading(true);
    try {
      await ApiService.updateTeamRole(game.id, currentTeam.id, newRole);
      Alert.alert('Success', `You are now a ${newRole}!`);
      onRefresh();
      onClose();
    } catch (error) {
      Alert.alert('Error', error instanceof Error ? error.message : 'Failed to update role');
    } finally {
      setLoading(false);
    }
  };

  const startNextRound = async () => {
    setLoading(true);
    try {
      await ApiService.startNextRound(game.id);
      Alert.alert('Success', 'Next round started!');
      onRefresh();
      onClose();
    } catch (error) {
      Alert.alert('Error', error instanceof Error ? error.message : 'Failed to start next round');
    } finally {
      setLoading(false);
    }
  };

  const seekers = game.teams.filter(t => t.role === 'seeker');
  const hiders = game.teams.filter(t => t.role === 'hider');

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <View style={styles.overlay}>
        <View style={styles.modal}>
          <Text style={styles.title}>Round {game.round} Setup</Text>
          <Text style={styles.subtitle}>Choose your role for the next round</Text>

          <View style={styles.currentTeamsSection}>
            <Text style={styles.sectionTitle}>Current Team Setup:</Text>
            <View style={styles.teamsContainer}>
              <View style={styles.teamColumn}>
                <Text style={styles.teamHeader}>Seekers ({seekers.length})</Text>
                {seekers.map(team => (
                  <Text key={team.id} style={[styles.teamName, team.id === currentTeam.id && styles.currentTeam]}>
                    {team.name} {team.id === currentTeam.id && '(You)'}
                  </Text>
                ))}
              </View>
              <View style={styles.teamColumn}>
                <Text style={styles.teamHeader}>Hiders ({hiders.length})</Text>
                {hiders.map(team => (
                  <Text key={team.id} style={[styles.teamName, team.id === currentTeam.id && styles.currentTeam]}>
                    {team.name} {team.id === currentTeam.id && '(You)'}
                  </Text>
                ))}
              </View>
            </View>
          </View>

          <Text style={styles.yourRole}>Your current role: <Text style={styles.roleText}>{currentTeam.role}</Text></Text>
          <Text style={styles.tokenInfo}>Your tokens: {currentTeam.tokens} (saved across rounds)</Text>
          {currentTeam.role === 'hider' && (
            <Text style={styles.hiderNote}>
              Note: As a hider, you cannot use tokens or do challenges. Your tokens are preserved for when you become a seeker.
            </Text>
          )}

          <View style={styles.buttonContainer}>
            <TouchableOpacity
              style={[styles.roleButton, styles.seekerButton, loading && styles.disabledButton]}
              onPress={() => updateRole('seeker')}
              disabled={loading}
            >
              <Text style={styles.buttonText}>Be a Seeker</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.roleButton, styles.hiderButton, loading && styles.disabledButton]}
              onPress={() => updateRole('hider')}
              disabled={loading}
            >
              <Text style={styles.buttonText}>Be a Hider</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.actionContainer}>
            <TouchableOpacity
              style={[styles.startButton, loading && styles.disabledButton]}
              onPress={startNextRound}
              disabled={loading}
            >
              <Text style={styles.startButtonText}>Start Round {game.round + 1}</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.cancelButton, loading && styles.disabledButton]}
              onPress={onClose}
              disabled={loading}
            >
              <Text style={styles.cancelButtonText}>Close</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modal: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 20,
    margin: 20,
    maxHeight: '80%',
    width: '90%',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 10,
    color: '#003366',
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 20,
    color: '#666',
  },
  currentTeamsSection: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#003366',
  },
  teamsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  teamColumn: {
    flex: 1,
    marginHorizontal: 5,
  },
  teamHeader: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 5,
    textAlign: 'center',
    color: '#003366',
  },
  teamName: {
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 3,
    color: '#666',
  },
  currentTeam: {
    fontWeight: 'bold',
    color: '#003366',
  },
  yourRole: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 20,
    color: '#333',
  },
  roleText: {
    fontWeight: 'bold',
    color: '#003366',
    textTransform: 'capitalize',
  },
  tokenInfo: {
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 10,
    color: '#666',
  },
  hiderNote: {
    fontSize: 12,
    textAlign: 'center',
    marginBottom: 20,
    color: '#FF9800',
    fontStyle: 'italic',
    paddingHorizontal: 10,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  roleButton: {
    flex: 1,
    padding: 15,
    borderRadius: 10,
    marginHorizontal: 5,
    alignItems: 'center',
  },
  seekerButton: {
    backgroundColor: '#4CAF50',
  },
  hiderButton: {
    backgroundColor: '#FF9800',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  actionContainer: {
    gap: 10,
  },
  startButton: {
    backgroundColor: '#003366',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  startButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  cancelButton: {
    backgroundColor: '#666',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: 'white',
    fontSize: 16,
  },
  disabledButton: {
    opacity: 0.5,
  },
});

export default RoleSelectionModal;
