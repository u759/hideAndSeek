import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  RefreshControl,
  Alert,
} from 'react-native';
import { MaterialIcons, FontAwesome, Entypo, Ionicons } from '@expo/vector-icons';
import { Game, Team } from '../types';
import ApiService from '../services/api';
import RoleSelectionModal from './RoleSelectionModal';

interface OverviewTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const OverviewTab: React.FC<OverviewTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [refreshing, setRefreshing] = React.useState(false);
  const [showRoleModal, setShowRoleModal] = useState(false);
  const [gameStats, setGameStats] = useState<any>(null);
  const [currentTime, setCurrentTime] = useState(Date.now());

  // Update current time every 30 seconds for live remaining time display
  React.useEffect(() => {
    const interval = setInterval(() => {
      setCurrentTime(Date.now());
    }, 30000); // Update every 30 seconds

    return () => clearInterval(interval);
  }, []);

  const handleRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await onRefresh();
    // Fetch game stats when game is ended
    if (game.status === 'ended') {
      try {
        const stats = await ApiService.getGameStats(game.id);
        setGameStats(stats);
      } catch (error) {
        console.error('Failed to fetch game stats:', error);
      }
    }
    setRefreshing(false);
  }, [onRefresh, game.status, game.id]);

  // Fetch game stats when component mounts if game is ended
  React.useEffect(() => {
    if (game.status === 'ended') {
      const fetchStats = async () => {
        try {
          const stats = await ApiService.getGameStats(game.id);
          console.log('Game stats:', stats);
          setGameStats(stats);
        } catch (error) {
          console.error('Failed to fetch game stats:', error);
        }
      };
      fetchStats();
    }
  }, [game.status, game.id]);

  const handleGameAction = async (action: 'start' | 'pause' | 'resume' | 'end' | 'restart') => {
    try {
      if (action === 'start') {
        await ApiService.startGame(game.id);
        Alert.alert('Success', 'Game started!');
      } else if (action === 'pause') {
        await ApiService.pauseGame(game.id);
        Alert.alert('Success', 'Game paused!');
      } else if (action === 'resume') {
        await ApiService.resumeGame(game.id);
        Alert.alert('Success', 'Game resumed!');
      } else if (action === 'end') {
        Alert.alert(
          'End Game',
          'Are you sure you want to end this game? This cannot be undone.',
          [
            { text: 'Cancel', style: 'cancel' },
            {
              text: 'End Game',
              style: 'destructive',
              onPress: async () => {
                try {
                  await ApiService.endGame(game.id);
                  Alert.alert('Success', 'Game ended!');
                  // No need to call onRefresh - WebSocket will update automatically
                } catch (error) {
                  Alert.alert('Error', 'Failed to end game. Please try again.');
                  console.error('Failed to end game:', error);
                }
              },
            },
          ]
        );
        return;
      } else if (action === 'restart') {
        Alert.alert(
          'Restart Game',
          'This will reset the game with the same teams. All progress will be lost.',
          [
            { text: 'Cancel', style: 'cancel' },
            {
              text: 'Restart',
              style: 'destructive',
              onPress: async () => {
                try {
                  await ApiService.restartGame(game.id);
                  Alert.alert('Success', 'Game restarted!');
                  // No need to call onRefresh - WebSocket will update automatically
                } catch (error) {
                  Alert.alert('Error', 'Failed to restart game. Please try again.');
                  console.error('Failed to restart game:', error);
                }
              },
            },
          ]
        );
        return;
      }
      // No need to call onRefresh - WebSocket will update automatically
    } catch (error) {
      let errorMessage = `Failed to ${action} game. Please try again.`;
      if (error instanceof Error && error.message) {
        errorMessage = error.message;
      }
      Alert.alert('Error', errorMessage);
      console.error(`Failed to ${action} game:`, error);
    }
  };

  const handleTestNotification = async () => {
    try {
      await ApiService.sendTestNotification(game.id, currentTeam.id);
      Alert.alert('Test Sent', 'Check if you received a test notification!');
    } catch (error) {
      Alert.alert('Error', `Failed to send test notification: ${error instanceof Error ? error.message : 'Unknown error'}`);
      console.error('Failed to send test notification:', error);
    }
  };

  const formatTime = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  };

  const getGameDuration = () => {
    let duration;

    if (game.status === 'ended' && game.endTime) {
      duration = game.endTime - game.startTime;
    } else {
      duration = currentTime - game.startTime;
      if (game.status === 'paused' && game.pauseTime) {
        duration -= (currentTime - game.pauseTime);
      }
    }

    if (game.totalPausedDuration) {
      duration -= game.totalPausedDuration;
    }
    
    duration = Math.max(0, duration);

    const minutes = Math.floor(duration / 60000);
    const hours = Math.floor(minutes / 60);
    if (hours > 0) {
      return `${hours}h ${minutes % 60}m`;
    }
    return `${minutes}m`;
  };

  const getRemainingTime = () => {
    if (!game.roundLengthMinutes || game.status === 'waiting' || game.status === 'ended') {
      return null;
    }

    let elapsed;
    if (game.status === 'paused' && game.pauseTime) {
      elapsed = game.pauseTime - game.startTime;
    } else {
      elapsed = currentTime - game.startTime;
    }

    // Subtract any paused time
    if (game.totalPausedDuration) {
      elapsed -= game.totalPausedDuration;
    }

    const elapsedMinutes = Math.floor(elapsed / 60000);
    const remainingMinutes = Math.max(0, game.roundLengthMinutes - elapsedMinutes);
    
    if (remainingMinutes === 0) {
      return 'Time Up!';
    }

    const hours = Math.floor(remainingMinutes / 60);
    const mins = remainingMinutes % 60;
    
    if (hours > 0) {
      return `${hours}h ${mins}m left`;
    }
    return `${mins}m left`;
  };

  const seekerTeams = game.teams.filter(t => t.role === 'seeker');
  const hiderTeams = game.teams.filter(t => t.role === 'hider');

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        style={styles.content}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
      >
        <View style={styles.header}>
          <Text style={styles.title}>Game Overview</Text>
          <Text style={styles.subtitle}>Round {game.round}</Text>
        </View>

        {/* Prominent Active Curses for Hiders */}
        {currentTeam.role === 'hider' && currentTeam.activeCurses && currentTeam.activeCurses.length > 0 && (
          <View style={styles.section}>
            <View style={styles.hiderCursesContainer}>
              <Text style={styles.hiderCursesTitle}>⚡ You are Cursed</Text>
              {currentTeam.activeCurses.map((ac, idx) => {
                const timeRemaining = Math.max(0, ac.endTime - Date.now());
                const minutesRemaining = Math.ceil(timeRemaining / 60000);
                return (
                  <View key={idx} style={styles.hiderCurseCard}>
                    <View style={styles.hiderCurseHeader}>
                      <Text style={styles.hiderCurseTitle}>{ac.curse.title}</Text>
                      <Text style={styles.hiderCurseTimer}>{minutesRemaining}m left</Text>
                    </View>
                    <Text style={styles.hiderCurseDescription}>{ac.curse.description}</Text>
                    {ac.completed ? (
                      <Text style={styles.completedBadge}>Completed ✔︎</Text>
                    ) : (
                      <TouchableOpacity
                        style={[styles.fullWidthButton, styles.startButton, { marginTop: 8, paddingVertical: 14 }]}
                        onPress={async () => {
                          try {
                            await ApiService.markCurseCompleted(game.id, currentTeam.id, ac.curse.id);
                            Alert.alert('Marked', 'Curse marked as completed.');
                            onRefresh();
                          } catch (e) {
                            Alert.alert('Error', e instanceof Error ? e.message : 'Failed to mark completed');
                          }
                        }}
                      >
                        <View style={styles.controlButtonContentCentered}>
                          <Ionicons name="checkmark-circle-outline" size={20} color="#fff" style={{ marginRight: 8 }} />
                          <Text style={styles.controlButtonText}>Mark Completed</Text>
                        </View>
                      </TouchableOpacity>
                    )}
                  </View>
                );
              })}
            </View>
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Game Status</Text>
          <View style={styles.statusCard}>
            <View style={styles.statusRow}>
              <Text style={styles.statusLabel}>Status:</Text>
              <Text style={[styles.statusValue, { color: getStatusColor(game.status) }]}>
                {game.status.toUpperCase()}
              </Text>
            </View>
            <View style={styles.statusRow}>
              <Text style={styles.statusLabel}>Duration:</Text>
              <Text style={styles.statusValue}>
                {game.status === 'waiting' ? '--' : getGameDuration()}
              </Text>
            </View>
            <View style={styles.statusRow}>
              <Text style={styles.statusLabel}>Started:</Text>
              <Text style={styles.statusValue}>
                {game.status === 'waiting' ? '--' : formatTime(game.startTime)}
              </Text>
            </View>
            {game.roundLengthMinutes && getRemainingTime() && (
              <View style={styles.statusRow}>
                <Text style={styles.statusLabel}>Round Time Remaining:</Text>
                <Text style={[
                  styles.statusValue, 
                  getRemainingTime() === 'Time Up!' ? { color: '#e74c3c', fontWeight: 'bold' } : {}
                ]}>
                  {getRemainingTime()}
                </Text>
              </View>
            )}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Game Controls</Text>
          <View style={styles.gameControlsContainer}>
            {game.status === 'waiting' && (
              <TouchableOpacity
                style={[styles.controlButton, styles.startButton]}
                onPress={() => handleGameAction('start')}
              >
                <View style={styles.controlButtonContent}>
                  <MaterialIcons name="play-circle-outline" size={24} color="#fff" style={{ marginRight: 8 }} />
                  <Text style={styles.controlButtonText}>Start Game</Text>
                </View>
              </TouchableOpacity>
            )}
            
            {/* Test Notification Button */}
            <TouchableOpacity
              style={[styles.controlButton, { backgroundColor: '#6B73FF' }]}
              onPress={() => handleTestNotification()}
            >
              <View style={styles.controlButtonContent}>
                <Ionicons name="notifications-outline" size={24} color="#fff" style={{ marginRight: 8 }} />
                <Text style={styles.controlButtonText}>Test Notification</Text>
              </View>
            </TouchableOpacity>
            
            {game.status === 'active' && (
              <>
                <TouchableOpacity
                  style={[styles.controlButton, styles.pauseButton]}
                  onPress={() => handleGameAction('pause')}
                >
                  <View style={styles.controlButtonContent}>
                    <MaterialIcons name="pause-circle-outline" size={24} color="#fff" style={{ marginRight: 8 }} />
                    <Text style={styles.controlButtonText}>Pause Game</Text>
                  </View>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.controlButton, styles.endButton]}
                  onPress={() => handleGameAction('end')}
                >
                  <View style={styles.controlButtonContent}>
                    <MaterialIcons name="cancel" size={24} color="#fff" style={{ marginRight: 8 }} />
                    <Text style={styles.controlButtonText}>End Game</Text>
                  </View>
                </TouchableOpacity>
              </>
            )}
            
            {game.status === 'paused' && (
              <>
                <TouchableOpacity
                  style={[styles.controlButton, styles.roleButton]}
                  onPress={() => setShowRoleModal(true)}
                >
                  <View style={styles.controlButtonContent}>
                    <MaterialIcons name="autorenew" size={24} color="#fff" style={{ marginRight: 8 }} />
                    <Text style={styles.controlButtonText}>Setup Next Round</Text>
                  </View>
                </TouchableOpacity>
                
                {/* Show different message and disable resume if paused by time limit */}
                {game.pausedByTimeLimit ? (
                  <View style={styles.timeLimitInfo}>
                    <View style={styles.controlButtonContent}>
                      <MaterialIcons name="schedule" size={24} color="#f39c12" style={{ marginRight: 8 }} />
                      <Text style={styles.timeLimitInfoText}>Round time limit reached</Text>
                    </View>
                    <Text style={styles.timeLimitHint}>Start a new round to continue</Text>
                  </View>
                ) : (
                  <TouchableOpacity
                    style={[styles.controlButton, styles.resumeButton]}
                    onPress={() => handleGameAction('resume')}
                  >
                    <View style={styles.controlButtonContent}>
                      <MaterialIcons name="play-arrow" size={24} color="#fff" style={{ marginRight: 8 }} />
                      <Text style={styles.controlButtonText}>Resume Game</Text>
                    </View>
                  </TouchableOpacity>
                )}
                
                <TouchableOpacity
                  style={[styles.controlButton, styles.endButton]}
                  onPress={() => handleGameAction('end')}
                >
                  <View style={styles.controlButtonContent}>
                    <MaterialIcons name="cancel" size={24} color="#fff" style={{ marginRight: 8 }} />
                    <Text style={styles.controlButtonText}>End Game</Text>
                  </View>
                </TouchableOpacity>
              </>
            )}
            
            {game.status === 'ended' && (
              <>
                <View style={styles.gameEndedInfo}>
                  <View style={styles.controlButtonContent}>
                    <FontAwesome name="flag" size={24} color="#7f8c8d" style={{ marginRight: 8 }} />
                    <Text style={styles.gameEndedInfoText}>Game has ended</Text>
                  </View>
                </View>
                <TouchableOpacity
                  style={[styles.controlButton, styles.startButton]}
                  onPress={() => handleGameAction('restart')}
                >
                  <View style={styles.controlButtonContent}>
                    <MaterialIcons name="replay" size={24} color="#fff" style={{ marginRight: 8 }} />
                    <Text style={styles.controlButtonText}>Restart Game</Text>
                  </View>
                </TouchableOpacity>
              </>
            )}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Your Team</Text>
          <View style={[styles.teamCard, { borderColor: getTeamColor(currentTeam.role) }]}>
            <View style={styles.teamHeader}>
              <Text style={styles.teamName}>{currentTeam.name}</Text>
              <Text style={[styles.teamRole, { color: getTeamColor(currentTeam.role) }]}>
                {currentTeam.role.toUpperCase()}
              </Text>
            </View>
            <View style={styles.teamStats}>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{currentTeam.tokens}</Text>
                <Text style={styles.statLabel}>Tokens</Text>
              </View>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{currentTeam.completedChallenges.length}</Text>
                <Text style={styles.statLabel}>Challenges</Text>
              </View>
              {currentTeam.role === 'hider' && (
                <View style={styles.statItem}>
                  <Text style={styles.statValue}>{currentTeam.activeCurses.length}</Text>
                  <Text style={styles.statLabel}>Active Curses</Text>
                </View>
              )}
            </View>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Game Status</Text>
          <View style={styles.statsRow}>
            <View style={styles.statItem}>
              <Text style={styles.statNumber}>{seekerTeams.length}</Text>
              <Text style={styles.statLabel}>Seekers</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={styles.statNumber}>{hiderTeams.length}</Text>
              <Text style={styles.statLabel}>Hiders</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={styles.statNumber}>{game.round}</Text>
              <Text style={styles.statLabel}>Round</Text>
            </View>
          </View>
          
          {game.status === 'ended' && (
            <View style={styles.gameEndedBanner}>
              <View style={styles.controlButtonContent}>
                <Text style={styles.gameEndedText}>🎉 Game Over!</Text>
              </View>
              {gameStats?.winner && (
                <Text style={styles.winnerText}>
                  {gameStats.winner.name} wins with {gameStats.winner.totalHiderTimeFormatted} of hiding time!
                </Text>
              )}
            </View>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>All Teams</Text>
          {seekerTeams.map((team) => (
            <View key={team.id} style={[styles.teamCard, { borderColor: '#27ae60' }]}>
              <View style={styles.teamHeader}>
                <Text style={styles.teamName}>
                  {team.name} {team.id === currentTeam.id ? '(You)' : ''}
                </Text>
                <Text style={[styles.teamRole, { color: '#27ae60' }]}>SEEKER</Text>
              </View>
              <Text style={styles.teamInfo}>Tokens: {team.tokens}</Text>
            </View>
          ))}
          
          {hiderTeams.map((team) => (
            <View key={team.id} style={[styles.teamCard, { borderColor: '#e74c3c' }]}>
              <View style={styles.teamHeader}>
                <Text style={styles.teamName}>
                  {team.name} {team.id === currentTeam.id ? '(You)' : ''}
                </Text>
                <Text style={[styles.teamRole, { color: '#e74c3c' }]}>HIDER</Text>
              </View>
              <Text style={styles.teamInfo}>
                Last seen: {team.location ? new Date(team.location.timestamp).toLocaleTimeString() : 'Unknown'}
              </Text>
            </View>
          ))}
        </View>

        

        {currentTeam.role === 'seeker' && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Quick Actions</Text>
            <Text style={styles.actionHint}>
              Use the tabs below to draw challenges and buy clues!
            </Text>
          </View>
        )}

        {currentTeam.role === 'hider' && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Hider Info</Text>
            <Text style={styles.actionHint}>
              Your location is being shared automatically. Stay hidden and watch out for seekers!
            </Text>
          </View>
        )}
      </ScrollView>

      <RoleSelectionModal
        visible={showRoleModal}
        game={game}
        currentTeam={currentTeam}
        onClose={() => setShowRoleModal(false)}
        onRefresh={onRefresh}
      />
    </SafeAreaView>
  );
};

const getStatusColor = (status: string) => {
  switch (status) {
    case 'active': return '#27ae60';
    case 'paused': return '#f39c12';
    case 'ended': return '#e74c3c';
    default: return '#95a5a6';
  }
};

const getTeamColor = (role: string) => {
  return role === 'seeker' ? '#27ae60' : '#e74c3c';
};

const styles = StyleSheet.create({
  controlButtonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
  },
  controlButtonContentCentered: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
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
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 12,
  },
  statusCard: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 16,
    color: '#666',
  },
  statusValue: {
    fontSize: 16,
    fontWeight: '600',
  },
  teamCard: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    borderWidth: 2,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  teamHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  teamName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  teamRole: {
    fontSize: 14,
    fontWeight: '600',
  },
  teamStats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 16,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#003366',
  },
  statNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#003366',
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  gameEndedBanner: {
    backgroundColor: '#e8f5e8',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#4caf50',
  },
  gameEndedText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2e7d32',
  },
  winnerText: {
    fontSize: 16,
    color: '#2e7d32',
    marginTop: 4,
  },
  teamInfo: {
    fontSize: 14,
    color: '#666',
  },
  actionHint: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    fontStyle: 'italic',
  },
  gameControlsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 12,
  },
  controlButton: {
    flex: 1,
    minWidth: '45%',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginVertical: 4,
  },
  fullWidthButton: {
  alignSelf: 'stretch',
  width: '100%',
  // ensure the button occupies the full width of the card's inner area
  paddingVertical: 12,
  paddingHorizontal: 16,
  borderRadius: 8,
  alignItems: 'center',
  flexDirection: 'row',
  justifyContent: 'center',
  },
  controlButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#fff',
  },
  startButton: {
    backgroundColor: '#27ae60',
  },
  pauseButton: {
    backgroundColor: '#f39c12',
  },
  resumeButton: {
    backgroundColor: '#3498db',
  },
  roleButton: {
    backgroundColor: '#9b59b6',
  },
  endButton: {
    backgroundColor: '#e74c3c',
  },
  gameEndedInfo: {
    backgroundColor: '#ecf0f1',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    width: '100%',
  },
  gameEndedInfoText: {
    fontSize: 16,
    color: '#7f8c8d',
    fontWeight: '600',
  },
  curseCard: {
    backgroundColor: '#fff',
    padding: 12,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#e74c3c',
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  curseHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  curseTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2c3e50',
    flex: 1,
  },
  curseTimer: {
    fontSize: 14,
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
  curseDescription: {
    fontSize: 14,
    color: '#34495e',
    lineHeight: 18,
  },
  hiderCursesContainer: {
  backgroundColor: '#fff7f7',
  borderWidth: 1,
  borderColor: '#f5c6cb',
  padding: 12,
  borderRadius: 10,
  width: '100%',
  alignSelf: 'stretch',
  },
  hiderCursesTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#c0392b',
    marginBottom: 8,
  },
  hiderCurseCard: {
    backgroundColor: '#fff',
    padding: 12,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#c0392b',
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  alignItems: 'stretch',
  },
  hiderCurseHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  hiderCurseTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#2c3e50',
    flex: 1,
  },
  hiderCurseTimer: {
    fontSize: 13,
    fontWeight: '700',
    color: '#fff',
    backgroundColor: '#c0392b',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
  },
  hiderCurseDescription: {
    fontSize: 14,
    color: '#34495e',
  },
  completedBadge: {
    marginTop: 8,
    alignSelf: 'flex-start',
    backgroundColor: '#e8f5e9',
    color: '#2e7d32',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    fontWeight: '700',
  },
  timeLimitInfo: {
    backgroundColor: '#fff8e7',
    borderWidth: 1,
    borderColor: '#f39c12',
    paddingVertical: 14,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginVertical: 8,
    alignSelf: 'stretch',
    width: '100%',
    minWidth: '100%',
    // ensure it sits as a full row inside the wrapping controls
    flexDirection: 'column',
  },
  timeLimitInfoText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#d35400',
  },
  timeLimitHint: {
    fontSize: 14,
    color: '#8e44ad',
    fontStyle: 'italic',
    textAlign: 'center',
    marginTop: 4,
  },
});

export default OverviewTab;
