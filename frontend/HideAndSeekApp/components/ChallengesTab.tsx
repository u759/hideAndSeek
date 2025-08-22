import React, { useEffect, useMemo, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  Alert,
  Modal,
  TextInput,
} from 'react-native';
import { Game, Team, DrawnCard, Challenge, Curse } from '../types';
import ApiService from '../services/api';

interface ChallengesTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const ChallengesTab: React.FC<ChallengesTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [drawnCard, setDrawnCard] = useState<DrawnCard | null>(null);
  const [loading, setLoading] = useState(false);
  const [showCardModal, setShowCardModal] = useState(false);
  const [challengeTitleById, setChallengeTitleById] = useState<Record<string, string>>({});
  const [customTokenInput, setCustomTokenInput] = useState<string>('0');
  const [showVariableRewardInput, setShowVariableRewardInput] = useState(false);

  // If the server reports an active challenge, reflect it as drawnCard
  const serverActiveChallenge = currentTeam.activeChallenge;
  useEffect(() => {
    if (serverActiveChallenge && !drawnCard) {
      // Normalize backend shape (token_reward or tokenReward) to frontend shape (token_count)
      const apiCard: any = serverActiveChallenge.challenge || {};
      const normalizedCard: Challenge = {
        id: apiCard.id,
        title: apiCard.title,
        description: apiCard.description,
        token_count: apiCard.token_count ?? apiCard.token_reward ?? apiCard.tokenReward ?? null,
      };
      setDrawnCard({ card: normalizedCard, type: 'challenge', remainingCards: 0 });
    }
    // If no server active challenge and we had local state, keep local state (user might be viewing during session)
  }, [serverActiveChallenge?.challenge?.id, serverActiveChallenge?.startTime]);

  // Build a map of challenge id -> title so we can render titles for completed IDs
  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const data = await ApiService.getChallengesAndCurses();
        const map: Record<string, string> = {};
        (data?.challenges || []).forEach((c: any) => {
          if (c?.id && c?.title) map[String(c.id)] = c.title;
        });
        if (mounted) setChallengeTitleById(map);
      } catch (_) {
        // non-fatal
      }
    })();
    return () => { mounted = false; };
  }, [game.id]);

  const canDrawCard = () => {
    if (game.status !== 'active') {
      return false;
    }
    if (currentTeam.vetoEndTime && Date.now() < currentTeam.vetoEndTime) {
      return false;
    }
    // Block draw if server still has an active challenge
    if (serverActiveChallenge) return false;
    return !drawnCard;
  };

  const getStatusMessage = () => {
    if (game.status === 'waiting') {
      return 'Game has not started yet. Challenges will be available once the game begins.';
    } else if (game.status === 'paused') {
      return 'Game is paused. All challenge activities are temporarily disabled.';
    } else if (game.status === 'ended') {
      return 'Game has ended. No more challenges can be drawn.';
    }
    return null;
  };

  const drawCard = async () => {
    setLoading(true);
    try {
      // include completedChallenges so backend can exclude them
      const response = await ApiService.drawCard(currentTeam.id, game.id, currentTeam.completedChallenges);

      // Normalize backend response to DrawnCard shape used by this component
      const normalized: DrawnCard = {
        card: response.card,
        type: response.type,
        remainingCards: (response as any).remainingChallenges,
      };

      setDrawnCard(normalized);
      setShowCardModal(true);
      setCustomTokenInput('0'); // Reset custom token input for new cards

    } catch (error: any) {
      Alert.alert('Error', error.message || 'Failed to draw card. Please try again.');
      console.error('Failed to draw card:', error);
    } finally {
      setLoading(false);
    }
  };

  const completeChallenge = () => {
    if (!drawnCard || drawnCard.type !== 'challenge') return;

    // Ask user to confirm before marking challenge complete
    Alert.alert(
      'Confirm Completion',
      'Are you sure you want to mark this challenge as completed? This action will award tokens to your team.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Confirm',
          onPress: async () => {
            // perform the original completion logic after confirmation
            try {
              // Dynamic challenge (user decides token count)
              if (drawnCard.card.token_count === null) {
                const customTokens = parseInt(customTokenInput || '0', 10);
                if (isNaN(customTokens) || customTokens < 0) {
                  Alert.alert('Error', 'Please enter a valid number of tokens (0 or greater).');
                  return;
                }

                const result = await ApiService.completeChallengeWithCustomTokens(
                  drawnCard.card.title,
                  currentTeam.id,
                  game.id,
                  customTokens
                );
                setDrawnCard(null);
                setShowCardModal(false);
                setCustomTokenInput('0');

                return;
              }

              // Regular challenge with fixed token_count
              const result = await ApiService.completeChallenge(
                drawnCard.card.title,
                currentTeam.id,
                game.id
              );
              setDrawnCard(null);
              setShowCardModal(false);
            } catch (error: any) {
              // If backend indicates this is actually a dynamic challenge, show the modal so user can enter tokens
              if (error?.message?.toLowerCase()?.includes('dynamic challenge')) {
                setShowCardModal(true);
                return;
              }
              Alert.alert('Error', error.message || 'Failed to complete challenge.');
              console.error('Failed to complete challenge:', error);
            }
          },
        },
      ]
    );
  };

  const vetoChallenge = async () => {
    if (!drawnCard) return;

    Alert.alert(
      'Veto Challenge',
      'Are you sure you want to veto this challenge? You will receive no tokens and be unable to draw cards for 5 minutes.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Veto',
          style: 'destructive',
          onPress: async () => {
            try {
              await ApiService.vetoChallenge(
                drawnCard.card.title,
                currentTeam.id,
                game.id
              );

              Alert.alert(
                'Challenge Vetoed',
                'You cannot draw another card for 5 minutes.',
                [
                  {
                    text: 'OK',
                    onPress: () => {
                      setDrawnCard(null);
                      setShowCardModal(false);
                      // No need to call onRefresh - parent will get WebSocket update automatically
                    }
                  }
                ]
              );
            } catch (error) {
              Alert.alert('Error', 'Failed to veto challenge.');
              console.error('Failed to veto challenge:', error);
            }
          }
        }
      ]
    );
  };

  const getVetoTimeRemaining = () => {
    if (!currentTeam.vetoEndTime) return 0;
    const remaining = currentTeam.vetoEndTime - Date.now();
    return Math.max(0, Math.ceil(remaining / 1000 / 60)); // minutes
  };

  const renderCard = () => {
    if (!drawnCard) return null;

    const card = drawnCard.card;
    const isChallenge = drawnCard.type === 'challenge';

    return (
      <View style={[styles.card, isChallenge ? styles.challengeCard : styles.curseCard]}>
        <Text style={styles.cardType}>
          {isChallenge ? '🎯 CHALLENGE' : '⚡ CURSE'}
        </Text>
        <Text style={styles.cardTitle}>{card.title}</Text>
        <Text style={styles.cardDescription}>{card.description}</Text>
        {isChallenge && (
          <View style={styles.rewardContainer}>
            <Text style={styles.cardTokens}>
              🪙 Reward: {card.token_count === null ? 'Variable (you decide)' : `${card.token_count} tokens`}
            </Text>
            {card.token_count === null && (
              <View style={styles.variableRewardInput}>
                <Text style={styles.inputLabel}>Enter tokens earned:</Text>
                <TextInput
                  style={styles.tokenInput}
                  value={customTokenInput}
                  onChangeText={setCustomTokenInput}
                  placeholder="0"
                  keyboardType="numeric"
                  maxLength={3}
                />
              </View>
            )}
          </View>
        )}
      </View>
    );
  };

  const vetoTimeRemaining = getVetoTimeRemaining();

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Challenge Cards</Text>
          <Text style={styles.subtitle}>
            Draw cards to earn tokens for buying clues
          </Text>
        </View>

        <View style={styles.tokenSection}>
          <Text style={styles.tokenTitle}>Current Tokens</Text>
          <Text style={styles.tokenValue}>{currentTeam.tokens}</Text>
        </View>

        <View style={styles.drawSection}>
          {getStatusMessage() && (
            <View style={styles.statusMessageContainer}>
              <Text style={styles.statusMessageText}>{getStatusMessage()}</Text>
            </View>
          )}

          {vetoTimeRemaining > 0 ? (
            <View style={styles.vetoWarning}>
              <Text style={styles.vetoText}>
                ⏰ Veto penalty active
              </Text>
              <Text style={styles.vetoTime}>
                {vetoTimeRemaining} minutes remaining
              </Text>
            </View>
          ) : drawnCard ? (
            <View style={styles.activeCardSection}>
              <Text style={styles.activeCardTitle}>Active Card</Text>
              {renderCard()}

              {drawnCard.type === 'challenge' ? (
                <View style={styles.cardActions}>
                  <TouchableOpacity
                    style={styles.completeButton}
                    onPress={completeChallenge}
                  >
                    <Text style={styles.completeButtonText}>Complete Challenge</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.vetoButton}
                    onPress={vetoChallenge}
                  >
                    <Text style={styles.vetoButtonText}>Veto (-5min penalty)</Text>
                  </TouchableOpacity>
                </View>
              ) : (
                <View style={styles.curseInfo}>
                  <Text style={styles.curseText}>
                    This curse has been applied to your team!
                  </Text>
                  <TouchableOpacity
                    style={styles.acknowledgeButton}
                    onPress={() => {
                      setDrawnCard(null);
                      setShowCardModal(false);
                    }}
                  >
                    <Text style={styles.acknowledgeButtonText}>Acknowledge</Text>
                  </TouchableOpacity>
                </View>
              )}
            </View>
          ) : (
            <TouchableOpacity
              style={[styles.drawButton, loading && styles.disabledButton]}
              onPress={drawCard}
              disabled={loading || !canDrawCard()}
            >
              <Text style={styles.drawButtonText}>
                {loading ? 'Drawing Card...' : '🎴 Draw Card'}
              </Text>
            </TouchableOpacity>
          )}
        </View>

        <View style={styles.statsSection}>
          <Text style={styles.statsTitle}>Challenge Statistics</Text>
          <View style={styles.statsGrid}>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{currentTeam.completedChallenges.length}</Text>
              <Text style={styles.statLabel}>Completed</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{currentTeam.tokens}</Text>
              <Text style={styles.statLabel}>Tokens Earned</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{currentTeam.activeCurses.length}</Text>
              <Text style={styles.statLabel}>Active Curses</Text>
            </View>
          </View>
        </View>

        {currentTeam.completedChallenges.length > 0 && (
          <View style={styles.historySection}>
            <Text style={styles.historyTitle}>Completed Challenges</Text>
            {currentTeam.completedChallenges.map((entry, index) => (
              <View key={index} style={styles.historyItem}>
                <Text style={styles.historyText}>✅ {challengeTitleById[entry] || entry}</Text>
              </View>
            ))}
          </View>
        )}
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
    textAlign: 'center',
  },
  tokenSection: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  tokenTitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 8,
  },
  tokenValue: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#003366',
  },
  drawSection: {
    marginBottom: 24,
  },
  vetoWarning: {
    backgroundColor: '#fff3cd',
    padding: 16,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#f39c12',
    alignItems: 'center',
  },
  vetoText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#856404',
    marginBottom: 4,
  },
  vetoTime: {
    fontSize: 14,
    color: '#856404',
  },
  drawButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  drawButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  activeCardSection: {
    marginBottom: 16,
  },
  activeCardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 12,
    textAlign: 'center',
  },
  card: {
    padding: 20,
    borderRadius: 8,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  challengeCard: {
    backgroundColor: '#e8f5e8',
    borderLeftWidth: 4,
    borderLeftColor: '#27ae60',
  },
  curseCard: {
    backgroundColor: '#ffeaea',
    borderLeftWidth: 4,
    borderLeftColor: '#e74c3c',
  },
  cardType: {
    fontSize: 14,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 12,
  },
  cardDescription: {
    fontSize: 16,
    lineHeight: 22,
    color: '#333',
    marginBottom: 12,
  },
  cardTokens: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#27ae60',
  },
  rewardContainer: {
    backgroundColor: '#e8f5e8',
    padding: 8,
    borderRadius: 6,
    marginTop: 0,
    alignItems: 'center',
  },
  cardActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  completeButton: {
    flex: 1,
    backgroundColor: '#27ae60',
    paddingVertical: 12,
    marginRight: 8,
    borderRadius: 6,
    alignItems: 'center',
  },
  completeButtonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  vetoButton: {
    flex: 1,
    backgroundColor: '#e74c3c',
    paddingVertical: 12,
    marginLeft: 8,
    borderRadius: 6,
    alignItems: 'center',
  },
  vetoButtonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  curseInfo: {
    alignItems: 'center',
  },
  curseText: {
    fontSize: 16,
    color: '#666',
    marginBottom: 12,
    textAlign: 'center',
  },
  acknowledgeButton: {
    backgroundColor: '#003366',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 6,
  },
  acknowledgeButtonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  statsSection: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    marginBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 16,
    textAlign: 'center',
  },
  statsGrid: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#003366',
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  historySection: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    marginBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  historyTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 16,
  },
  historyItem: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  historyText: {
    fontSize: 16,
    color: '#333',
  },
  statusMessageContainer: {
    backgroundColor: '#fff3cd',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#f39c12',
  },
  statusMessageText: {
    fontSize: 16,
    color: '#856404',
    textAlign: 'center',
    fontWeight: '600',
  },
  variableRewardInput: {
    marginTop: 12,
    alignItems: 'center',
  },
  inputLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8,
  },
  tokenInput: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 16,
    textAlign: 'center',
    backgroundColor: '#fff',
    minWidth: 80,
  },
});

export default ChallengesTab;
