import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Alert } from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';

import OverviewTab from '../components/OverviewTab';
import ChallengesTab from '../components/ChallengesTab';
import CluesTab from '../components/CluesTab';
import LocationTab from '../components/LocationTab';
import FindHidersTab from '../components/FindHidersTab';
import { RootStackParamList, TabParamList, Game, Team } from '../types';
import ApiService from '../services/api';
import useLocationTracker from '../hooks/useLocationTracker';

type GameScreenRouteProp = RouteProp<RootStackParamList, 'Game'>;

const Tab = createBottomTabNavigator<TabParamList>();

const GameScreen: React.FC = () => {
  const route = useRoute<GameScreenRouteProp>();
  const { gameId, teamId } = route.params;
  
  const [game, setGame] = useState<Game | null>(null);
  const [currentTeam, setCurrentTeam] = useState<Team | null>(null);
  const [loading, setLoading] = useState(true);

  // Location tracking for hiders
  useLocationTracker({
    teamId,
    gameId,
    isHider: currentTeam?.role === 'hider',
    isActive: game?.status === 'active',
  });

  useEffect(() => {
    loadGame();
  }, [gameId]);

  const loadGame = async () => {
    try {
      const gameData = await ApiService.getGame(gameId);
      setGame(gameData);
      
      const team = gameData.teams.find(t => t.id === teamId);
      setCurrentTeam(team || null);
    } catch (error) {
      Alert.alert('Error', 'Failed to load game data');
      console.error('Failed to load game:', error);
    } finally {
      setLoading(false);
    }
  };

  const refreshGame = () => {
    loadGame();
  };

  if (loading || !game || !currentTeam) {
    return <View style={styles.container} />;
  }

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ focused, color, size }) => {
          let iconName: keyof typeof Ionicons.glyphMap;

          if (route.name === 'Overview') {
            iconName = focused ? 'home' : 'home-outline';
          } else if (route.name === 'Challenges') {
            iconName = focused ? 'card' : 'card-outline';
          } else if (route.name === 'Clues') {
            iconName = focused ? 'search' : 'search-outline';
          } else if (route.name === 'Location') {
            iconName = focused ? 'location' : 'location-outline';
          } else if (route.name === 'FindHiders') {
            iconName = focused ? 'eye' : 'eye-outline';
          } else {
            iconName = 'ellipse';
          }

          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: '#003366',
        tabBarInactiveTintColor: 'gray',
        headerShown: false,
      })}
    >
      <Tab.Screen name="Overview">
        {() => (
          <OverviewTab 
            game={game} 
            currentTeam={currentTeam} 
            onRefresh={refreshGame}
          />
        )}
      </Tab.Screen>
      
      {currentTeam.role === 'seeker' && (
        <>
          <Tab.Screen name="Challenges">
            {() => (
              <ChallengesTab 
                game={game} 
                currentTeam={currentTeam} 
                onRefresh={refreshGame}
              />
            )}
          </Tab.Screen>
          <Tab.Screen name="Clues">
            {() => (
              <CluesTab 
                game={game} 
                currentTeam={currentTeam} 
                onRefresh={refreshGame}
              />
            )}
          </Tab.Screen>
          <Tab.Screen name="FindHiders">
            {() => (
              <FindHidersTab 
                game={game} 
                currentTeam={currentTeam} 
                onRefresh={refreshGame}
              />
            )}
          </Tab.Screen>
        </>
      )}
      
      {currentTeam.role === 'hider' && (
        <Tab.Screen name="Location">
          {() => (
            <LocationTab 
              game={game} 
              currentTeam={currentTeam} 
              onRefresh={refreshGame}
            />
          )}
        </Tab.Screen>
      )}
    </Tab.Navigator>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default GameScreen;
