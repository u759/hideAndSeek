import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';
import { StyleSheet } from 'react-native';
import * as BackgroundTask from 'expo-background-task';
import * as TaskManager from 'expo-task-manager';

import HomeScreen from './screens/HomeScreen';
import RoleSelectionScreen from './screens/RoleSelectionScreen';
import JoinGameScreen from './screens/JoinGameScreen';
import GameSetupScreen from './screens/GameSetupScreen';
import GameLobbyScreen from './screens/GameLobbyScreen';
import TeamJoinScreen from './screens/TeamJoinScreen';
import GameScreen from './screens/GameScreen';
import { RootStackParamList } from './types';
// Ensure background tasks are registered on app init
import './backgroundTasks';
import { createNotificationChannelsAsync } from './hooks/usePushNotifications';

const Stack = createStackNavigator<RootStackParamList>();

export default function App() {
  useEffect(() => {
    // Configure background fetch on app startup
    const setupBackgroundFetch = async () => {
      try {
  // Ensure Android channels exist before any notifications arrive (background/cold)
  await createNotificationChannelsAsync();

        const status = await BackgroundTask.getStatusAsync();
        console.log('Background task status:', status);
        
        if (status === BackgroundTask.BackgroundTaskStatus.Available) {
          console.log('Background task is available');
        } else {
          console.log('Background task is not available');
        }
      } catch (e) {
        console.log('Failed to setup background task:', e);
      }
    };

    setupBackgroundFetch();
  }, []);

  return (
    <NavigationContainer>
      <StatusBar style="auto" />
      <Stack.Navigator 
        initialRouteName="Home"
        screenOptions={{
          headerStyle: {
            backgroundColor: '#003366',
          },
          headerTintColor: '#fff',
          headerTitleStyle: {
            fontWeight: 'bold',
          },
        }}
      >
        <Stack.Screen 
          name="Home" 
          component={HomeScreen} 
          options={{ title: 'UBCeek: Hide & Seek' }}
        />
        <Stack.Screen 
          name="RoleSelection" 
          component={RoleSelectionScreen} 
          options={{ title: 'Choose Role' }}
        />
        <Stack.Screen 
          name="JoinGame" 
          component={JoinGameScreen} 
          options={{ title: 'Join Game' }}
        />
        <Stack.Screen 
          name="GameSetup" 
          component={GameSetupScreen} 
          options={{ title: 'Game Setup' }}
        />
        <Stack.Screen 
          name="GameLobby" 
          component={GameLobbyScreen} 
          options={{ title: 'Game Lobby' }}
        />
        <Stack.Screen 
          name="TeamJoin" 
          component={TeamJoinScreen} 
          options={{ title: 'Select Team' }}
        />
        <Stack.Screen 
          name="Game" 
          component={GameScreen} 
          options={{ title: 'Game in Progress' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
