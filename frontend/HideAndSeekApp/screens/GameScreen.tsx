import React, { useState, useEffect } from 'react';
import { View, StyleSheet, Alert, Text } from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';

import OverviewTab from '../components/OverviewTab';
import ChallengesTab from '../components/ChallengesTab';
import CursesTab from '../components/CursesTab';
import CluesTab from '../components/CluesTab';
import LocationTab from '../components/LocationTab';
import FindHidersTab from '../components/FindHidersTab';
import HiderClueListener from '../components/HiderClueListener';
import SeekerClueListener from '../components/SeekerClueListener';
import { RootStackParamList, TabParamList, Game, Team } from '../types';
import useGameState from '../hooks/useGameState';
import useLocationTracker from '../hooks/useLocationTracker';
import usePushNotifications from '../hooks/usePushNotifications';

type GameScreenRouteProp = RouteProp<RootStackParamList, 'Game'>;

const Tab = createBottomTabNavigator<TabParamList>();

const GameTabs: React.FC<{
  game: Game;
  currentTeam: Team;
  teamId: string;
  gameId: string;
  onRefresh: () => void;
}> = ({ game, currentTeam, teamId, gameId, onRefresh }) => {
  // Location tracking for hiders is now here, ensuring it only runs when data is loaded
  useLocationTracker({
    teamId,
    gameId,
    isHider: currentTeam.role === 'hider',
    isActive: game.status === 'active',
  });
  // Register push notifications token for this team/game
  usePushNotifications(gameId, teamId);

  return (
    <>
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ focused, color, size }) => {
          let iconName: keyof typeof Ionicons.glyphMap;

          if (route.name === 'Overview') {
            iconName = focused ? 'home' : 'home-outline';
          } else if (route.name === 'Challenges') {
            iconName = focused ? 'card' : 'card-outline';
          } else if (route.name === 'Curses') {
            iconName = focused ? 'flash' : 'flash-outline';
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
            onRefresh={onRefresh}
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
                onRefresh={onRefresh}
              />
            )}
          </Tab.Screen>
            <Tab.Screen name="Clues">
              {() => (
                <CluesTab 
                  game={game} 
                  currentTeam={currentTeam} 
                  onRefresh={onRefresh}
                />
              )}
            </Tab.Screen>
            <Tab.Screen name="Curses">
              {() => (
                <CursesTab 
                  game={game} 
                  currentTeam={currentTeam} 
                  onRefresh={onRefresh}
                />
              )}
            </Tab.Screen>
          <Tab.Screen name="FindHiders">
            {() => (
              <FindHidersTab 
                game={game} 
                currentTeam={currentTeam} 
                onRefresh={onRefresh}
              />
            )}
          </Tab.Screen>
        </>
      )}
      
      {currentTeam.role === 'hider' && (
        <>
          <Tab.Screen name="Location">
            {() => (
              <LocationTab 
                game={game} 
                currentTeam={currentTeam} 
                onRefresh={onRefresh}
              />
            )}
          </Tab.Screen>
        </>
      )}
    </Tab.Navigator>
    {/* Mount the hider clue listener outside tabs so it runs immediately */}
    {currentTeam.role === 'hider' && (
      <HiderClueListener gameId={gameId} teamId={currentTeam.id} />
    )}
    {/* Mount the seeker listener so they get popups for incoming clues on any tab */}
    {currentTeam.role === 'seeker' && (
      <SeekerClueListener gameId={gameId} teamId={currentTeam.id} />
    )}
    </>
  );
};

const GameScreen: React.FC = () => {
  const route = useRoute<GameScreenRouteProp>();
  const { gameId, teamId } = route.params;
  
  // Use centralized game state management with WebSocket updates
  const { game, currentTeam, loading, error, connected, refresh } = useGameState({
    gameId,
    teamId,
    enabled: true,
  });

  // Show error if WebSocket is disconnected for too long
  useEffect(() => {
    if (error) {
      console.error('Game state error:', error);
      // Don't show alerts for every error, as WebSocket will reconnect automatically
    }
  }, [error]);

  const refreshGame = () => {
    refresh();
  };

  if (loading || !game || !currentTeam) {
    return (
      <View style={styles.container}>
        <Text>Loading Game...</Text>
      </View>
    );
  }

  return (
    <GameTabs
      game={game}
      currentTeam={currentTeam}
      teamId={teamId}
      gameId={gameId}
      onRefresh={refreshGame}
    />
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
