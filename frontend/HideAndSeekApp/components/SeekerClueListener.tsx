import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Modal, SafeAreaView, StyleSheet, Text, View, Pressable, Image } from 'react-native';
import useGameWebSocket from '../hooks/useGameWebSocket';
import { getWebsocketUrl } from '../config/api';
import ApiService from '../services/api';

interface Props {
  gameId: string;
  teamId: string; // seeker team id
}

type ClueResponseMsg = {
  type: 'clueResponse';
  requestingTeamId: string;
  response: {
    requestId: string;
    clueTypeId: string; // e.g., 'selfie' | 'closest-building' | ...
    responseType: 'photo' | 'text' | 'location' | 'automatic';
    responseData: string; // text or URL
    timestamp: number;
  };
};

// Shows a global popup when a new clue (selfie or closest landmark) arrives for the seeker team
const SeekerClueListener: React.FC<Props> = ({ gameId, teamId }) => {
  const [visible, setVisible] = useState(false);
  const [payload, setPayload] = useState<ClueResponseMsg['response'] | null>(null);
  const lastSeenTsRef = useRef<number>(0);

  const wsUrl = useMemo(() => getWebsocketUrl(), []);

  // Initialize last-seen timestamp on mount to avoid popping old clues
  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const history = await ApiService.getClueHistory(gameId, teamId);
        if (!mounted) return;
        const latestRelevant = history
          ?.filter((c) => !!c && typeof c.text === 'string')
          .reduce((acc, c) => (c.timestamp > acc ? c.timestamp : acc), 0) || 0;
        lastSeenTsRef.current = latestRelevant;
      } catch {
        // ignore
      }
    })();
    return () => { mounted = false; };
  }, [gameId, teamId]);

  useGameWebSocket({
    wsUrl,
    gameId,
    onMessage: (msg: any) => {
      const data = msg as ClueResponseMsg;
      if (data?.type !== 'clueResponse') return;
      if (data.requestingTeamId !== teamId) return;

      const clueId = data.response?.clueTypeId;
      if (clueId === 'selfie' || clueId === 'closest-building') {
        // Update last seen to this response time
        lastSeenTsRef.current = Math.max(lastSeenTsRef.current, data.response.timestamp || Date.now());
        setPayload(data.response);
        setVisible(true);
      }
    },
  });

  // Polling fallback: check history and show newest relevant clue not yet seen
  useEffect(() => {
    let canceled = false;
    const poll = async () => {
      try {
        const history = await ApiService.getClueHistory(gameId, teamId);
        if (canceled || !history?.length) return;
        const newest = [...history]
          .filter((c) => !!c)
          .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))[0];
        if (!newest) return;
        if ((newest.timestamp || 0) <= lastSeenTsRef.current) return;
        // Only popup for selfie clues (we can reliably detect by URL pattern)
        // For closest-building, we rely on the WebSocket message which has proper clueTypeId
        const looksLikeImage = /\/api\/uploads\/files\//.test(newest.text);
        if (looksLikeImage) {
          // We don't have the original response meta here, but we can synthesize minimal payload
          setPayload({
            requestId: 'history',
            clueTypeId: 'selfie',
            responseType: 'photo',
            responseData: newest.text,
            timestamp: newest.timestamp,
          });
          setVisible(true);
          lastSeenTsRef.current = newest.timestamp || Date.now();
        }
      } catch {
        // ignore
      }
    };
    const id = setInterval(poll, 10000);
    return () => { canceled = true; clearInterval(id); };
  }, [gameId, teamId]);

  const dismiss = () => {
    setVisible(false);
    setPayload(null);
  };

  if (!payload) return null;

  // Only show this custom modal for selfie and closest-building clues.
  const allowedClue = payload.clueTypeId === 'selfie' || payload.clueTypeId === 'closest-building';
  if (!allowedClue) {
    // For other clue types, do not render the custom popup here.
    return null;
  }

  const isImage = payload.clueTypeId === 'selfie' || /\/api\/uploads\/files\//.test(payload.responseData);
  const title = payload.clueTypeId === 'selfie' ? 'Selfie received' : 'Clue';

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <SafeAreaView style={styles.backdrop}>
        <View style={styles.card}>
          <Text style={styles.title}>{title}</Text>
          {!isImage && (
            <Text style={styles.clueText}>{payload.responseData}</Text>
          )}
          {isImage && (
            <Image source={{ uri: payload.responseData }} style={styles.image} resizeMode="cover" />
          )}
          <Pressable onPress={dismiss} style={[styles.button, styles.primary]}>
            <Text style={styles.buttonText}>OK</Text>
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
  clueText: { fontSize: 16, color: '#111', marginBottom: 12, lineHeight: 22 },
  image: { width: '100%', height: 260, borderRadius: 8, marginBottom: 12 },
  button: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    minWidth: 120,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'flex-end',
  },
  primary: { backgroundColor: '#003366' },
  buttonText: { color: 'white', fontWeight: '600' },
});

export default SeekerClueListener;
