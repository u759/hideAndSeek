import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  Image,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList } from '../types';

type HomeScreenNavigationProp = StackNavigationProp<RootStackParamList, 'Home'>;

const HomeScreen: React.FC = () => {
  const navigation = useNavigation<HomeScreenNavigationProp>();

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>UBCeek</Text>
          <Text style={styles.subtitle}>Hide & Seek across UBC</Text>
        </View>

        <View style={styles.description}>
          <Text style={styles.descriptionText}>
            Teams compete to hide the longest amount of time across UBC campus.
          </Text>
          <Text style={styles.descriptionText}>
            • Seekers can draw challenge cards to earn tokens
          </Text>
          <Text style={styles.descriptionText}>
            • Use tokens to buy clues about hider locations
          </Text>
          <Text style={styles.descriptionText}>
            • Hiders share their location automatically
          </Text>
        </View>

        <TouchableOpacity 
          style={[styles.startButton, styles.secondaryButton]}
          onPress={() => navigation.navigate('JoinGame')}
        >
          <Text style={[styles.startButtonText, styles.secondaryButtonText]}>Join Existing Game</Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.startButton, styles.tertiaryButton]}
          onPress={() => navigation.navigate('GameSetup')}
        >
          <Text style={[styles.startButtonText, styles.tertiaryButtonText]}>Create Group Game</Text>
        </TouchableOpacity>

        <View style={styles.footer}>
          <Text style={styles.footerText}>
            Ready to explore UBC and have fun? Let's play!
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
    fontSize: 48,
    fontWeight: 'bold',
    color: '#003366',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 18,
    color: '#666',
    textAlign: 'center',
  },
  description: {
    marginVertical: 40,
  },
  descriptionText: {
    fontSize: 16,
    color: '#333',
    lineHeight: 24,
    marginBottom: 10,
    textAlign: 'center',
  },
  startButton: {
    backgroundColor: '#003366',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: 'center',
    marginVertical: 10,
  },
  secondaryButton: {
    backgroundColor: 'transparent',
    borderWidth: 2,
    borderColor: '#003366',
  },
  tertiaryButton: {
    backgroundColor: '#4CAF50',
  },
  startButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  secondaryButtonText: {
    color: '#003366',
  },
  tertiaryButtonText: {
    color: '#fff',
  },
  footer: {
    alignItems: 'center',
    marginBottom: 20,
  },
  footerText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
});

export default HomeScreen;
