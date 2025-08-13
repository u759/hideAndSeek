import React from 'react';
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
import { Game, Team } from '../types';
import ApiService from '../services/api';

interface OverviewTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const OverviewTab: React.FC<OverviewTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [refreshing, setRefreshing] = React.useState(false);

  const handleRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await onRefresh();
    setRefreshing(false);
  }, [onRefresh]);

  const handleGameAction = async (action: 'start' | 'pause' | 'resume' | 'end') => {
    try {
      let updatedGame: Game;
      
      switch (action) {
        case 'start':
          updatedGame = await ApiService.startGame(game.id);
          Alert.alert('Success', 'Game started!');
          break;
        case 'pause':
          updatedGame = await ApiService.pauseGame(game.id);
          Alert.alert('Success', 'Game paused!');
          break;
        case 'resume':
          updatedGame = await ApiService.resumeGame(game.id);
          Alert.alert('Success', 'Game resumed!');
          break;
        case 'end':
          Alert.alert(
            'End Game',
            'Are you sure you want to end this game? This cannot be undone.',
            [
              { text: 'Cancel', style: 'cancel' },
              {
                text: 'End Game',
                style: 'destructive',
                onPress: async () => {
                  updatedGame = await ApiService.endGame(game.id);
                  Alert.alert('Success', 'Game ended!');
                  onRefresh();
                },
              },
            ]
          );
          return;
      }
      
      onRefresh(); // Refresh the game data
    } catch (error) {
      Alert.alert('Error', `Failed to ${action} game. Please try again.`);
      console.error(`Failed to ${action} game:`, error);
    }
  };

  const formatTime = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  };

  const getGameDuration = () => {
    let duration = Date.now() - game.startTime;
    
    // Subtract total paused time
    if (game.totalPausedDuration) {
      duration -= game.totalPausedDuration;
    }
    
    // If currently paused, subtract current pause duration
    if (game.status === 'paused' && game.pauseTime) {
      duration -= (Date.now() - game.pauseTime);
    }
    
    const minutes = Math.floor(duration / 60000);
    const hours = Math.floor(minutes / 60);
    if (hours > 0) {
      return `${hours}h ${minutes % 60}m`;
    }
    return `${minutes}m`;
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
                <Text style={styles.controlButtonText}>🎮 Start Game</Text>
              </TouchableOpacity>
            )}
            
            {game.status === 'active' && (
              <>
                <TouchableOpacity
                  style={[styles.controlButton, styles.pauseButton]}
                  onPress={() => handleGameAction('pause')}
                >
                  <Text style={styles.controlButtonText}>⏸️ Pause Game</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.controlButton, styles.endButton]}
                  onPress={() => handleGameAction('end')}
                >
                  <Text style={styles.controlButtonText}>🛑 End Game</Text>
                </TouchableOpacity>
              </>
            )}
            
            {game.status === 'paused' && (
              <>
                <TouchableOpacity
                  style={[styles.controlButton, styles.resumeButton]}
                  onPress={() => handleGameAction('resume')}
                >
                  <Text style={styles.controlButtonText}>▶️ Resume Game</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.controlButton, styles.endButton]}
                  onPress={() => handleGameAction('end')}
                >
                  <Text style={styles.controlButtonText}>🛑 End Game</Text>
                </TouchableOpacity>
              </>
            )}
            
            {game.status === 'ended' && (
              <View style={styles.gameEndedInfo}>
                <Text style={styles.gameEndedInfoText}>🏁 Game has ended</Text>
              </View>
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
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{currentTeam.activeCurses.length}</Text>
                <Text style={styles.statLabel}>Active Curses</Text>
              </View>
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
              <Text style={styles.gameEndedText}>🎉 Game Over!</Text>
              {hiderTeams.length === 1 && (
                <Text style={styles.winnerText}>{hiderTeams[0].name} wins!</Text>
              )}
            </View>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>All Teams</Text>
          {seekerTeams.map((team) => (
            <View key={team.id} style={[styles.teamCard, { borderColor: '#e74c3c' }]}>
              <View style={styles.teamHeader}>
                <Text style={styles.teamName}>
                  {team.name} {team.id === currentTeam.id ? '(You)' : ''}
                </Text>
                <Text style={[styles.teamRole, { color: '#e74c3c' }]}>SEEKER</Text>
              </View>
              <Text style={styles.teamInfo}>Tokens: {team.tokens}</Text>
            </View>
          ))}
          
          {hiderTeams.map((team) => (
            <View key={team.id} style={[styles.teamCard, { borderColor: '#27ae60' }]}>
              <View style={styles.teamHeader}>
                <Text style={styles.teamName}>
                  {team.name} {team.id === currentTeam.id ? '(You)' : ''}
                </Text>
                <Text style={[styles.teamRole, { color: '#27ae60' }]}>HIDER</Text>
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
  return role === 'seeker' ? '#e74c3c' : '#27ae60';
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
});

export default OverviewTab;
