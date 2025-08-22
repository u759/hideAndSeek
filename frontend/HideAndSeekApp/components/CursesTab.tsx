import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  Modal,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { Game, Team } from '../types';
import ApiService from '../services/api';
import useGameWebSocket from '../hooks/useGameWebSocket';
import { getWebsocketUrl } from '../config/api';

interface CursesTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

interface TargetSelectionModalProps {
  visible: boolean;
  onClose: () => void;
  onSelectTarget: (targetTeamId: string) => void;
  availableTargets: Team[];
  loading: boolean;
}

const TargetSelectionModal: React.FC<TargetSelectionModalProps> = ({
  visible,
  onClose,
  onSelectTarget,
  availableTargets,
  loading,
}) => {
  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet">
      <View style={styles.modalContainer}>
        <View style={styles.modalHeader}>
          <Text style={styles.modalTitle}>Select Team to Curse</Text>
          <TouchableOpacity onPress={onClose} style={styles.closeButton}>
            <MaterialIcons name="close" size={24} color="#666" />
          </TouchableOpacity>
        </View>

        <ScrollView style={styles.modalContent}>
          <Text style={styles.modalDescription}>
            Choose a hider team to apply a random curse to:
          </Text>
          
          {availableTargets.length === 0 ? (
            <Text style={styles.noTargetsText}>
              No teams available to curse. All hider teams currently have active curses.
            </Text>
          ) : (
            availableTargets.map((team) => (
              <TouchableOpacity
                key={team.id}
                style={styles.targetItem}
                onPress={() => onSelectTarget(team.id)}
                disabled={loading}
              >
                <View style={styles.targetInfo}>
                  <Text style={styles.targetName}>{team.name}</Text>
                  <Text style={styles.targetRole}>HIDER</Text>
                </View>
                <MaterialIcons name="arrow-forward" size={24} color="#666" />
              </TouchableOpacity>
            ))
          )}
        </ScrollView>
      </View>
    </Modal>
  );
};

const CursesTab: React.FC<CursesTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [availableTargets, setAvailableTargets] = useState<Team[]>([]);
  const [showTargetModal, setShowTargetModal] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchAvailableTargets();
  }, []);

  // Listen for websocket game updates and refresh available targets when relevant
  const wsUrl = getWebsocketUrl();

  useGameWebSocket({
    wsUrl,
    gameId: game.id,
    onMessage: (data: any) => {
      // refresh targets when game state or curses change
      if (data?.type === 'gameUpdate' || data?.type === 'curse_update' || data?.type === 'clue_response') {
        fetchAvailableTargets();
        // No need to call onRefresh - parent will get WebSocket update automatically
      }
    },
  });

  const fetchAvailableTargets = async () => {
    try {
      const targets = await ApiService.getAvailableCurseTargets(game.id, currentTeam.id);
      setAvailableTargets(targets);
    } catch (error) {
      console.error('Failed to fetch available targets:', error);
    }
  };

  const handleApplyCurse = async (targetTeamId: string) => {
    setLoading(true);
    try {
      const result = await ApiService.applyCurse(game.id, currentTeam.id, targetTeamId);
      
      Alert.alert(
        'Curse Applied!', 
        `Successfully applied "${result.curse.title}" to ${result.targetTeam.name}!`
      );
      
      setShowTargetModal(false);
      await fetchAvailableTargets(); // Refresh available targets
      // No need to call onRefresh - parent will get WebSocket update automatically
    } catch (error) {
      Alert.alert('Error', error instanceof Error ? error.message : 'Failed to apply curse');
    } finally {
      setLoading(false);
    }
  };

  const getActiveAppliedCurses = () => {
    const currentTime = Date.now();
    return currentTeam.appliedCurses?.filter(curse => curse.endTime > currentTime) || [];
  };

  if (currentTeam.role !== 'seeker') {
    return (
      <View style={styles.container}>
        <View style={styles.errorContainer}>
          <MaterialIcons name="block" size={48} color="#e74c3c" />
          <Text style={styles.errorText}>Only seekers can access the curses feature.</Text>
        </View>
      </View>
    );
  }

  // disable if the team doesn't have enough tokens or game isn't active
  const insufficientTokens = (currentTeam.tokens ?? 0) < 10;
  const gameActive = game.status === 'active';

  const getStatusMessage = () => {
    if (game.status === 'waiting') return 'Game has not started yet. Curses are available once the game begins.';
    if (game.status === 'paused') return 'Game is paused. Curse actions are temporarily disabled.';
    if (game.status === 'ended') return 'Game has ended. Curses are no longer available.';
    return null;
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollContainer}>
        <Text style={styles.header}>⚡ Curse Magic</Text>
        <Text style={styles.description}>
          Apply random curses to hider teams to hinder their movement and gain strategic advantages!
        </Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Apply Random Curse</Text>
          <Text style={styles.sectionDescription}>
            The curse will be chosen randomly and applied to your selected target team.
          </Text>
          <View style={styles.costInfo}>
            <MaterialIcons name="token" size={20} color="#f39c12" />
            <Text style={styles.costText}>Cost: 10 tokens per curse</Text>
          </View>
          
          <TouchableOpacity
            style={[
              styles.applyButton,
              (availableTargets.length === 0 || loading || insufficientTokens || !gameActive) && styles.disabledButton,
            ]}
            onPress={() => {
              if (!gameActive) return;
              fetchAvailableTargets();
              setShowTargetModal(true);
            }}
            disabled={availableTargets.length === 0 || loading || insufficientTokens || !gameActive}
          >
            <MaterialIcons name="flash-on" size={24} color="white" />
            <Text style={styles.applyButtonText}>
              {loading ? 'Applying...' : insufficientTokens ? 'Need 10 tokens' : 'Apply Curse (10 tokens)'}
            </Text>
          </TouchableOpacity>
          
          {availableTargets.length === 0 && (
            <Text style={styles.noTargetsText}>
              All hider teams currently have active curses. Wait for them to expire.
            </Text>
          )}
          {!gameActive && (
            <View style={styles.statusContainer}>
              <Text style={styles.statusText}>{getStatusMessage()}</Text>
            </View>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Your Active Curses</Text>
          {getActiveAppliedCurses().length === 0 ? (
            <Text style={styles.noCursesText}>You haven't applied any curses yet.</Text>
          ) : (
            getActiveAppliedCurses().map((appliedCurse, index) => {
              const timeRemaining = Math.max(0, appliedCurse.endTime - Date.now());
              const minutesRemaining = Math.ceil(timeRemaining / 60000);
              const targetTeam = game.teams.find(t => t.id === appliedCurse.targetTeamId);
              const isCompleted = !!targetTeam?.activeCurses?.some(ac => ac.curse.id === appliedCurse.curse.id && ac.endTime > Date.now() && ac.completed);
              
              return (
                <View key={index} style={styles.activeCurseCard}>
                  <View style={styles.curseCardHeader}>
                    <Text style={styles.curseCardTitle}>{appliedCurse.curse.title}</Text>
                    <Text style={styles.curseTimer}>{minutesRemaining}m left</Text>
                  </View>
                  <Text style={styles.curseTarget}>Target: {appliedCurse.targetTeamName}</Text>
                  <Text style={styles.curseCardDescription}>{appliedCurse.curse.description}</Text>
                  {appliedCurse.curse.penalty && appliedCurse.curse.penalty > 0 && !isCompleted && (
                    <Text style={styles.penaltyWarning}>
                      ⚠️ Penalty: +{appliedCurse.curse.penalty}s hiding time if not completed
                    </Text>
                  )}
                  {isCompleted && (
                    <Text style={styles.completedBadge}>Completed ✔︎</Text>
                  )}
                  <Text style={styles.curseCost}>Cost: {appliedCurse.curse.token_count} tokens</Text>
                </View>
              );
            })
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>All Active Curses</Text>
          <Text style={styles.sectionDescription}>
            See all curses currently affecting hider teams across the game.
          </Text>
          {(() => {
            const allActiveCurses = game.teams
              .filter(team => team.role === 'hider' && team.activeCurses && team.activeCurses.length > 0)
              .flatMap(team =>
                team.activeCurses.map(activeCurse => ({
                  ...activeCurse,
                  teamName: team.name,
                  teamId: team.id,
                  completed: activeCurse.completed,
                }))
              );

            if (allActiveCurses.length === 0) {
              return (
                <Text style={styles.noCursesText}>No teams are currently cursed</Text>
              );
            }

            return allActiveCurses.map((curse, index) => {
              const timeRemaining = Math.max(0, curse.endTime - Date.now());
              const minutesRemaining = Math.ceil(timeRemaining / 60000);
              
              return (
                <View key={index} style={styles.activeCurseCard}>
                  <View style={styles.curseCardHeader}>
                    <Text style={styles.curseCardTitle}>{curse.curse.title}</Text>
                    <Text style={styles.curseTimer}>{minutesRemaining}m left</Text>
                  </View>
                  <Text style={styles.curseTarget}>Cursed Team: {curse.teamName}</Text>
                  <Text style={styles.curseCardDescription}>{curse.curse.description}</Text>
                  {curse.curse.penalty && curse.curse.penalty > 0 && !curse.completed && (
                    <Text style={styles.penaltyWarning}>
                      ⚠️ Penalty: +{curse.curse.penalty}s hiding time if not completed
                    </Text>
                  )}
                  {curse.completed && (
                    <Text style={styles.completedBadge}>Completed ✔︎</Text>
                  )}
                </View>
              );
            });
          })()}
        </View>

        <View style={styles.infoSection}>
          <MaterialIcons name="info" size={24} color="#3498db" />
          <Text style={styles.infoText}>
            Curses are applied randomly and all teams will be notified when a curse is active. 
            The duration and cost of each curse varies.
          </Text>
        </View>
      </ScrollView>

      <TargetSelectionModal
        visible={showTargetModal}
        onClose={() => setShowTargetModal(false)}
        onSelectTarget={handleApplyCurse}
        availableTargets={availableTargets}
        loading={loading}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollContainer: {
    flex: 1,
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    margin: 20,
    color: '#2c3e50',
  },
  description: {
    fontSize: 16,
    textAlign: 'center',
    marginHorizontal: 20,
    marginBottom: 20,
    color: '#7f8c8d',
  },
  section: {
    backgroundColor: 'white',
    margin: 16,
    padding: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#2c3e50',
    marginBottom: 8,
  },
  sectionDescription: {
    fontSize: 14,
    color: '#7f8c8d',
    marginBottom: 12,
  },
  costInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff3cd',
    padding: 12,
    borderRadius: 8,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#ffeaa7',
  },
  costText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#856404',
    marginLeft: 8,
  },
  applyButton: {
    backgroundColor: '#e74c3c',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 25,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  applyButtonText: {
    color: 'white',
    fontWeight: '600',
    fontSize: 16,
  },
  disabledButton: {
    backgroundColor: '#bdc3c7',
  },
  noTargetsText: {
    fontSize: 14,
    color: '#7f8c8d',
    textAlign: 'center',
    padding: 16,
    fontStyle: 'italic',
  },
  noCursesText: {
    fontSize: 14,
    color: '#7f8c8d',
    textAlign: 'center',
    padding: 16,
    fontStyle: 'italic',
  },
  activeCurseCard: {
    backgroundColor: '#fff',
    padding: 12,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#e74c3c',
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  curseCardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  curseCardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2c3e50',
    flex: 1,
  },
  curseTimer: {
    fontSize: 12,
    fontWeight: '600',
    color: '#e74c3c',
    backgroundColor: '#fee',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 12,
  },
  curseTarget: {
    fontSize: 14,
    color: '#7f8c8d',
    fontWeight: '500',
    marginBottom: 4,
  },
  curseCardDescription: {
    fontSize: 14,
    color: '#34495e',
    marginBottom: 4,
  },
  curseCost: {
    fontSize: 12,
    color: '#7f8c8d',
    fontStyle: 'italic',
  },
  completedBadge: {
    marginTop: 6,
    alignSelf: 'flex-start',
    backgroundColor: '#e8f5e9',
    color: '#2e7d32',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    fontWeight: '700',
  },
  penaltyWarning: {
    marginTop: 6,
    alignSelf: 'flex-start',
    backgroundColor: '#fff3e0',
    color: '#f57c00',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    fontWeight: '600',
    fontSize: 12,
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  errorText: {
    fontSize: 18,
    color: '#e74c3c',
    textAlign: 'center',
    marginTop: 16,
  },
  infoSection: {
    backgroundColor: '#ecf0f1',
    margin: 16,
    padding: 16,
    borderRadius: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  infoText: {
    fontSize: 14,
    color: '#34495e',
    flex: 1,
    lineHeight: 20,
  },
  statusContainer: {
    marginTop: 12,
    padding: 12,
    backgroundColor: '#fff3cd',
    borderRadius: 8,
    marginHorizontal: 16,
    alignItems: 'center',
  },
  statusText: {
    color: '#856404',
    fontSize: 14,
    textAlign: 'center',
  },
  
  // Modal styles
  modalContainer: {
    flex: 1,
    backgroundColor: 'white',
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#2c3e50',
  },
  closeButton: {
    padding: 4,
  },
  modalContent: {
    flex: 1,
    padding: 16,
  },
  modalDescription: {
    fontSize: 16,
    color: '#7f8c8d',
    marginBottom: 20,
    textAlign: 'center',
  },
  targetItem: {
    backgroundColor: '#f8f9fa',
    padding: 16,
    borderRadius: 8,
    marginBottom: 8,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#dee2e6',
  },
  targetInfo: {
    flex: 1,
  },
  targetName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2c3e50',
  },
  targetRole: {
    fontSize: 12,
    color: '#27ae60',
    backgroundColor: '#e8f5e8',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
    alignSelf: 'flex-start',
    marginTop: 4,
  },
});

export default CursesTab;
