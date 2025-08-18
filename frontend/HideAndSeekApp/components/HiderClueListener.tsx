import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Modal, Pressable, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { API_BASE_URL } from '../config/api';
import ApiService from '../services/api';
import useGameWebSocket from '../hooks/useGameWebSocket';

type ClueRequestPayload = {
  id: string;
  clueTypeId: string;
  clueTypeName?: string;
  requestingTeamName?: string;
  responseType: 'photo' | 'text' | 'location' | 'automatic';
  description?: string;
  requestTimestamp?: number;
  expirationTimestamp?: number;
};

interface Props {
  gameId: string;
  teamId: string;
}

// Derive WebSocket URL from API_BASE_URL (http://host:port/api -> ws://host:port/ws)
const getWsUrl = () => {
  const httpBase = API_BASE_URL.replace(/\/?api$/, '');
  return httpBase.replace(/^http/, 'ws') + '/ws';
};

const HiderClueListener: React.FC<Props> = ({ gameId, teamId }) => {
  const [visible, setVisible] = useState(false);
  const [request, setRequest] = useState<ClueRequestPayload | null>(null);
  const [textResponse, setTextResponse] = useState('');
  const [timeLeft, setTimeLeft] = useState<number | null>(null);

  const wsUrl = useMemo(getWsUrl, []);

  useGameWebSocket({
    wsUrl,
    gameId,
    onMessage: (data) => {
      if (data?.type === 'clueRequest' && data?.targetTeamId === teamId) {
        const req = data.request as ClueRequestPayload;
        setRequest(req);
        setVisible(true);
        updateTimer(req.expirationTimestamp || null);
      }
    },
  });

  // Polling fallback in case WS misses an event or reconnects
  useEffect(() => {
    let mounted = true;
    const fetchPending = async () => {
      try {
        const list: ClueRequestPayload[] = await ApiService.getPendingClueRequests(gameId, teamId);
        if (mounted && list && list.length > 0) {
          // Show the first pending one if none showing
          if (!visible) {
            const req = list[0];
            setRequest(req);
            setVisible(true);
            updateTimer(req.expirationTimestamp || null);
          }
        }
      } catch (_) {
        // ignore
      }
    };
    const id = setInterval(fetchPending, 10000);
    // also run once soon after mount
    setTimeout(fetchPending, 1000);
    return () => {
      mounted = false;
      clearInterval(id);
    };
  }, [gameId, teamId, visible]);

  // Countdown timer
  useEffect(() => {
    if (timeLeft == null) return;
    if (timeLeft <= 0) return;
    const id = setInterval(() => setTimeLeft((t) => (t == null ? null : Math.max(0, t - 1))), 1000);
    return () => clearInterval(id);
  }, [timeLeft]);

  const updateTimer = (expiration: number | null) => {
    if (!expiration) {
      setTimeLeft(null);
      return;
    }
    const seconds = Math.max(0, Math.floor((expiration - Date.now()) / 1000));
    setTimeLeft(seconds);
  };

  const dismiss = () => {
    setVisible(false);
    setRequest(null);
    setTextResponse('');
    setTimeLeft(null);
  };

  const submitText = async () => {
    if (!request) return;
    if (!textResponse.trim()) {
      Alert.alert('Enter a clue', 'Please type your landmark or answer.');
      return;
    }
    try {
      await ApiService.respondToClueRequest(request.id, teamId, textResponse.trim());
      Alert.alert('Clue sent', 'Your response was delivered to the seekers.');
      dismiss();
    } catch (e: any) {
      Alert.alert('Failed to send', e?.message || 'Unknown error');
    }
  };

  const pickImage = async (fromCamera: boolean) => {
    try {
      if (fromCamera) {
        const { status } = await ImagePicker.requestCameraPermissionsAsync();
        if (status !== 'granted') {
          Alert.alert('Camera permission needed');
          return;
        }
        const result = await ImagePicker.launchCameraAsync({ quality: 0.6, base64: false });
        if (!result.canceled) {
          const uri = result.assets?.[0]?.uri;
          if (uri) await upload(uri);
        }
      } else {
        const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (status !== 'granted') {
          Alert.alert('Library permission needed');
          return;
        }
        const result = await ImagePicker.launchImageLibraryAsync({ quality: 0.6, base64: false });
        if (!result.canceled) {
          const uri = result.assets?.[0]?.uri;
          if (uri) await upload(uri);
        }
      }
    } catch (e: any) {
      Alert.alert('Selfie failed', e?.message || 'Unknown error');
    }
  };

  const upload = async (uri: string) => {
    if (!request) return;
    try {
      await ApiService.uploadSelfie(request.id, teamId, uri);
      Alert.alert('Selfie sent', 'Your selfie was delivered to the seekers.');
      dismiss();
    } catch (e: any) {
      Alert.alert('Upload failed', e?.message || 'Unknown error');
    }
  };

  if (!request) return null;

  const isPhoto = request.responseType === 'photo';
  const isText = request.responseType === 'text';

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <SafeAreaView style={styles.backdrop}>
        <View style={styles.card}>
          <Text style={styles.title}>Clue request for your team</Text>
          {request.clueTypeName ? (
            <Text style={styles.subtitle}>{request.clueTypeName}</Text>
          ) : null}
          {request.description ? (
            <Text style={styles.desc}>{request.description}</Text>
          ) : null}
          {timeLeft != null && (
            <Text style={styles.timer}>Expires in: {timeLeft}s</Text>
          )}

          {isText && (
            <View style={{ width: '100%' }}>
              <TextInput
                placeholder="Type your landmark or answer"
                value={textResponse}
                onChangeText={setTextResponse}
                style={styles.input}
                multiline
              />
              <Pressable onPress={submitText} style={[styles.button, styles.primary]}>
                <Text style={styles.buttonText}>Send</Text>
              </Pressable>
            </View>
          )}

          {isPhoto && (
            <View style={styles.row}>
              <Pressable onPress={() => pickImage(true)} style={[styles.button, styles.primary]}>
                <Text style={styles.buttonText}>Take Photo</Text>
              </Pressable>
              <Pressable onPress={() => pickImage(false)} style={[styles.button, styles.secondary]}>
                <Text style={styles.buttonText}>Choose</Text>
              </Pressable>
            </View>
          )}

          <Pressable onPress={dismiss} style={styles.linkBtn}>
            <Text style={styles.link}>Later</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  card: {
    width: '100%',
    maxWidth: 520,
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowOffset: { width: 0, height: 4 },
    shadowRadius: 12,
    elevation: 4,
  },
  title: { fontSize: 18, fontWeight: '600', marginBottom: 6 },
  subtitle: { fontSize: 16, fontWeight: '500', marginBottom: 8 },
  desc: { color: '#333', marginBottom: 8 },
  timer: { color: '#b00020', marginBottom: 12 },
  row: { flexDirection: 'row', gap: 12, justifyContent: 'space-between' },
  button: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    minWidth: 120,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: { backgroundColor: '#003366' },
  secondary: { backgroundColor: '#4a6fa5' },
  buttonText: { color: 'white', fontWeight: '600' },
  linkBtn: { marginTop: 12, alignItems: 'center' },
  link: { color: '#003366' },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    padding: 10,
    minHeight: 80,
    marginBottom: 12,
  },
});

export default HiderClueListener;
