import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  Alert,
  FlatList,
} from 'react-native';
import { Game, Team, ClueType, Clue } from '../types';
import ApiService from '../services/api';

interface CluesTabProps {
  game: Game;
  currentTeam: Team;
  onRefresh: () => void;
}

const CluesTab: React.FC<CluesTabProps> = ({ game, currentTeam, onRefresh }) => {
  const [clueTypes, setClueTypes] = useState<ClueType[]>([]);
  const [purchasedClues, setPurchasedClues] = useState<Clue[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadClueTypes();
    loadClueHistory();
  }, [game.id]);

  const loadClueTypes = async () => {
    try {
      const types = await ApiService.getClueTypes();
      setClueTypes(types);
    } catch (error) {
      console.error('Failed to load clue types:', error);
    }
  };

  const loadClueHistory = async () => {
    try {
      const history = await ApiService.getClueHistory(game.id);
      setPurchasedClues(history);
    } catch (error) {
      console.error('Failed to load clue history:', error);
    }
  };

  const purchaseClue = async (clueType: ClueType) => {
    if (currentTeam.tokens < clueType.cost) {
      Alert.alert('Insufficient Tokens', `You need ${clueType.cost} tokens to purchase this clue.`);
      return;
    }

    Alert.alert(
      'Purchase Clue',
      `Purchase "${clueType.name}" for ${clueType.cost} tokens?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Purchase',
          onPress: async () => {
            setLoading(true);
            try {
              const result = await ApiService.purchaseClue(
                clueType.id,
                game.id,
                currentTeam.id
              );
              
              Alert.alert(
                'Clue Purchased!',
                `Clue: ${result.clue.content}`,
                [
                  {
                    text: 'OK',
                    onPress: () => {
                      onRefresh();
                      loadClueHistory();
                    }
                  }
                ]
              );
            } catch (error) {
              Alert.alert('Error', 'Failed to purchase clue. Please try again.');
              console.error('Failed to purchase clue:', error);
            } finally {
              setLoading(false);
            }
          }
        }
      ]
    );
  };

  const renderClueType = ({ item }: { item: ClueType }) => {
    const canAfford = currentTeam.tokens >= item.cost;

    return (
      <TouchableOpacity
        style={[styles.clueCard, !canAfford && styles.unaffordableCard]}
        onPress={() => purchaseClue(item)}
        disabled={loading || !canAfford}
      >
        <View style={styles.clueHeader}>
          <Text style={styles.clueName}>{item.name}</Text>
          <View style={[styles.costBadge, !canAfford && styles.unaffordableBadge]}>
            <Text style={[styles.costText, !canAfford && styles.unaffordableCostText]}>
              {item.cost} 🪙
            </Text>
          </View>
        </View>
        <Text style={styles.clueDescription}>{item.description}</Text>
        {!canAfford && (
          <Text style={styles.insufficientText}>
            Need {item.cost - currentTeam.tokens} more tokens
          </Text>
        )}
      </TouchableOpacity>
    );
  };

  const renderPurchasedClue = ({ item }: { item: Clue }) => {
    const timeAgo = new Date(item.timestamp).toLocaleTimeString();
    
    return (
      <View style={styles.purchasedClueCard}>
        <View style={styles.purchasedClueHeader}>
          <Text style={styles.purchasedClueName}>{item.type.name}</Text>
          <Text style={styles.purchasedClueTime}>{timeAgo}</Text>
        </View>
        <Text style={styles.purchasedClueContent}>{item.content}</Text>
        <Text style={styles.purchasedClueCost}>Cost: {item.type.cost} tokens</Text>
      </View>
    );
  };

  const hiderTeams = game.teams.filter(t => t.role === 'hider');

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Buy Clues</Text>
          <Text style={styles.subtitle}>
            Spend tokens to get hints about hider locations
          </Text>
        </View>

        <View style={styles.tokenSection}>
          <Text style={styles.tokenTitle}>Available Tokens</Text>
          <Text style={styles.tokenValue}>{currentTeam.tokens}</Text>
        </View>

        <View style={styles.gameInfo}>
          <Text style={styles.gameInfoTitle}>Active Hiders</Text>
          {hiderTeams.map((team, index) => (
            <Text key={team.id} style={styles.hiderName}>
              • {team.name}
            </Text>
          ))}
          <Text style={styles.gameInfoNote}>
            Clues will be assigned to a random hider team
          </Text>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Available Clues</Text>
          <FlatList
            data={clueTypes}
            renderItem={renderClueType}
            keyExtractor={(item) => item.id}
            scrollEnabled={false}
            showsVerticalScrollIndicator={false}
          />
        </View>

        {purchasedClues.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Purchased Clues</Text>
            <FlatList
              data={purchasedClues}
              renderItem={renderPurchasedClue}
              keyExtractor={(item) => item.id}
              scrollEnabled={false}
              showsVerticalScrollIndicator={false}
            />
          </View>
        )}

        {purchasedClues.length === 0 && (
          <View style={styles.emptyState}>
            <Text style={styles.emptyStateText}>
              No clues purchased yet. Complete challenges to earn tokens!
            </Text>
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
  gameInfo: {
    backgroundColor: '#e8f4fd',
    padding: 16,
    borderRadius: 8,
    marginBottom: 24,
    borderLeftWidth: 4,
    borderLeftColor: '#003366',
  },
  gameInfoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 8,
  },
  hiderName: {
    fontSize: 14,
    color: '#333',
    marginBottom: 4,
  },
  gameInfoNote: {
    fontSize: 12,
    color: '#666',
    marginTop: 8,
    fontStyle: 'italic',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 16,
  },
  clueCard: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: '#003366',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  unaffordableCard: {
    borderColor: '#ccc',
    opacity: 0.6,
  },
  clueHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  clueName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#003366',
    flex: 1,
  },
  costBadge: {
    backgroundColor: '#003366',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  unaffordableBadge: {
    backgroundColor: '#ccc',
  },
  costText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 14,
  },
  unaffordableCostText: {
    color: '#666',
  },
  clueDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  insufficientText: {
    fontSize: 12,
    color: '#e74c3c',
    marginTop: 8,
    fontStyle: 'italic',
  },
  purchasedClueCard: {
    backgroundColor: '#e8f5e8',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#27ae60',
  },
  purchasedClueHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  purchasedClueName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#27ae60',
  },
  purchasedClueTime: {
    fontSize: 12,
    color: '#666',
  },
  purchasedClueContent: {
    fontSize: 16,
    color: '#333',
    marginBottom: 8,
    lineHeight: 22,
  },
  purchasedClueCost: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
  },
  emptyState: {
    backgroundColor: '#fff',
    padding: 40,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 24,
  },
  emptyStateText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 22,
  },
});

export default CluesTab;
