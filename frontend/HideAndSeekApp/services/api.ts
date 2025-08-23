import { Game, Team, DrawnCard, ClueType, Clue, Location, Curse } from '../types';
import { API_BASE_URL } from '../config/api';

class ApiService {
  // Game endpoints
  async createGame(teamNames: string[], roundLengthMinutes?: number): Promise<Game> {
    try {
      console.log(`Making request to: ${API_BASE_URL}/game`);
      const body: any = { teamNames };
      if (roundLengthMinutes) {
        body.roundLengthMinutes = roundLengthMinutes;
      }

      const response = await fetch(`${API_BASE_URL}/game`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        const errorMessage = errorData?.error || `Failed to create game: ${response.status}`;
        throw new Error(errorMessage);
      }

      return response.json();
    } catch (error) {
      console.error('Network error in createGame:', error);
      throw new Error(`Failed to create game: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  async createGameWithRole(teamNames: string[], playerRole: 'seeker' | 'hider', roundLengthMinutes?: number): Promise<Game> {
    try {
      console.log(`Making request to: ${API_BASE_URL}/game`);
      const body: any = { teamNames, playerRole };
      if (roundLengthMinutes) {
        body.roundLengthMinutes = roundLengthMinutes;
      }

      const response = await fetch(`${API_BASE_URL}/game`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        const errorMessage = errorData?.error || `Failed to create game: ${response.status}`;
        throw new Error(errorMessage);
      }

      return response.json();
    } catch (error) {
      console.error('Network error in createGameWithRole:', error);
      throw new Error(`Failed to create game: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  async getGame(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}`);

    if (!response.ok) {
      throw new Error('Failed to fetch game');
    }

    return response.json();
  }

  async getGameByCode(gameCode: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/code/${gameCode}`);

    if (!response.ok) {
      throw new Error('Game not found');
    }

    return response.json();
  }

  async updateGameStatus(gameId: string, status: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ status }),
    });

    if (!response.ok) {
      throw new Error('Failed to update game status');
    }

    return response.json();
  }

  async startGame(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/start`, {
      method: 'POST',
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      const errorMessage = errorData?.error || 'Failed to start game';
      throw new Error(errorMessage);
    }

    return response.json();
  }

  async endGame(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/end`, {
      method: 'POST',
    });

    if (!response.ok) {
      throw new Error('Failed to end game');
    }

    return response.json();
  }

  async pauseGame(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/pause`, {
      method: 'POST',
    });

    if (!response.ok) {
      throw new Error('Failed to pause game');
    }

    return response.json();
  }

  async resumeGame(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/resume`, {
      method: 'POST',
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      const errorMessage = errorData?.error || 'Failed to resume game';
      throw new Error(errorMessage);
    }

    return response.json();
  }

  async nextRound(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/next-round`, {
      method: 'POST',
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      const errorMessage = errorData?.error || 'Failed to start next round';
      throw new Error(errorMessage);
    }

    return response.json();
  }

  async restartGame(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/restart`, {
      method: 'POST',
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      const errorMessage = errorData?.error || 'Failed to restart game';
      throw new Error(errorMessage);
    }

    return response.json();
  }

  async updateTeamTokens(gameId: string, teamId: string, tokens: number): Promise<Team> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/teams/${teamId}/tokens`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ tokens }),
    });

    if (!response.ok) {
      throw new Error('Failed to update team tokens');
    }

    return response.json();
  }

  async switchTeamRole(gameId: string, teamId: string, role: 'seeker' | 'hider', foundByTeamId?: string) {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/teams/${teamId}/role`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ role, foundByTeamId }),
    });

    if (!response.ok) {
      throw new Error('Failed to switch team role');
    }

    return response.json();
  }

  async markHiderFound(gameId: string, hiderId: string, seekerId: string) {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/teams/${hiderId}/found`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ seekerId }),
    });

    if (!response.ok) {
      throw new Error('Failed to mark hider as found');
    }

    return response.json();
  }

  async getGameStats(gameId: string) {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/stats`);

    if (!response.ok) {
      throw new Error('Failed to get game stats');
    }

    return response.json();
  }

  // Challenge endpoints
  async getChallengesAndCurses() {
    const response = await fetch(`${API_BASE_URL}/challenges`);

    if (!response.ok) {
      throw new Error('Failed to fetch challenges');
    }

    return response.json();
  }

  async drawCard(teamId: string, gameId: string, completedChallenges?: string[]): Promise<DrawnCard> {
    const response = await fetch(`${API_BASE_URL}/challenges/draw`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ teamId, gameId, completedChallenges }),
    });

    if (response.status === 429) {
      // Handle veto penalty
      const errorData = await response.json();
      throw new Error(errorData.message || 'Cannot draw cards due to veto penalty');
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.error || errorData.message || 'Failed to draw card');
    }

    return response.json();
  }

  async completeChallenge(challengeTitle: string, teamId: string, gameId: string) {
    const response = await fetch(`${API_BASE_URL}/challenges/complete`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ challengeTitle, teamId, gameId }),
    });

    if (!response.ok) {
      throw new Error('Failed to complete challenge');
    }

    return response.json();
  }

  async completeChallengeWithCustomTokens(challengeTitle: string, teamId: string, gameId: string, customTokens: number) {
    const response = await fetch(`${API_BASE_URL}/challenges/complete-custom`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ challengeTitle, teamId, gameId, customTokens }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.error || 'Failed to complete challenge with custom tokens');
    }

    return response.json();
  }

  async vetoChallenge(challengeTitle: string, teamId: string, gameId: string) {
    const response = await fetch(`${API_BASE_URL}/challenges/veto`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ challengeTitle, teamId, gameId }),
    });

    if (!response.ok) {
      throw new Error('Failed to veto challenge');
    }

    return response.json();
  }

  // Curse endpoints
  async applyCurse(gameId: string, seekerTeamId: string, targetTeamId: string) {
    const response = await fetch(`${API_BASE_URL}/curse/apply`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ gameId, seekerTeamId, targetTeamId }),
    });

    if (!response.ok) {
      throw new Error('Failed to apply curse');
    }

    return response.json();
  }

  async getAvailableCurseTargets(gameId: string, seekerTeamId: string): Promise<Team[]> {
    const response = await fetch(`${API_BASE_URL}/curse/targets/${gameId}/${seekerTeamId}`);

    if (!response.ok) {
      throw new Error('Failed to fetch available curse targets');
    }

    return response.json();
  }

  async markCurseCompleted(gameId: string, teamId: string, curseId: string) {
    const response = await fetch(`${API_BASE_URL}/curse/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameId, teamId, curseId }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to mark curse completed');
      } catch (_) {
        throw new Error('Failed to mark curse completed');
      }
    }
    return response.json();
  }

  async acknowledgeCurse(gameId: string, teamId: string, curseId: string) {
    const response = await fetch(`${API_BASE_URL}/curse/acknowledge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameId, teamId, curseId }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to acknowledge curse');
      } catch (_) {
        throw new Error('Failed to acknowledge curse');
      }
    }
    return response.json();
  }

  // Clue endpoints
  async getClueTypes(): Promise<ClueType[]> {
    const response = await fetch(`${API_BASE_URL}/clues/types`);

    if (!response.ok) {
      throw new Error('Failed to fetch clue types');
    }

    return response.json();
  }

  async getPendingClueRequests(gameId: string, teamId: string) {
    const response = await fetch(`${API_BASE_URL}/clues/${gameId}/teams/${teamId}/requests`);
    if (!response.ok) {
      throw new Error('Failed to fetch pending clue requests');
    }
    return response.json();
  }

  async respondToClueRequest(requestId: string, teamId: string, responseData: string) {
    const response = await fetch(`${API_BASE_URL}/clues/requests/${requestId}/respond`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ teamId, responseData }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to submit clue response');
      } catch (_) {
        throw new Error('Failed to submit clue response');
      }
    }
    return response.json();
  }

  async uploadSelfie(requestId: string, teamId: string, gameId: string, fileUri: string) {
    const formData = new FormData();
    // React Native FormData file object shape; cast to any to appease TypeScript
    formData.append(
      'file',
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ({ uri: fileUri, name: 'selfie.jpg', type: 'image/jpeg' } as any)
    );
    formData.append('requestId', requestId);
    formData.append('teamId', teamId);
    formData.append('gameId', gameId);

    const response = await fetch(`${API_BASE_URL}/uploads/selfie`, {
      method: 'POST',
      // Don't set Content-Type manually; let fetch set multipart boundary
      body: formData as any,
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to upload selfie');
      } catch (_) {
        throw new Error('Failed to upload selfie');
      }
    }
    return response.json();
  }

  async purchaseClue(clueTypeId: string, gameId: string, purchasingTeamId: string, description: string) {
    const response = await fetch(`${API_BASE_URL}/clues/purchase`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ clueTypeId, gameId, purchasingTeamId, description }),
    });

    if (!response.ok) {
      let errorMessage = `Failed to purchase clue (HTTP ${response.status})`;
      try {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const err = await response.json();
          errorMessage = err?.error || errorMessage;
        } else {
          errorMessage = await response.text() || errorMessage;
        }
      } catch (parseErr) {
        // Try to get text even if JSON parsing fails
        try {
          errorMessage = await response.text() || errorMessage;
        } catch {
          errorMessage = "Failed to purchase clue";
        }
      }
      throw new Error(errorMessage);
    }

    return response.json();
  }

  async getClueHistory(gameId: string, teamId: string): Promise<Clue[]> {
    const response = await fetch(`${API_BASE_URL}/clues/${gameId}/teams/${teamId}/history`);
    if (!response.ok) {
      try {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const err = await response.json();
          throw new Error(err?.error || 'Failed to fetch clue history');
        } else {
          const text = await response.text();
          throw new Error(text || 'Failed to fetch clue history');
        }
      } catch (_) {
        throw new Error('Failed to fetch clue history');
      }
    }
    return response.json();
  }

  async updateTeamRole(gameId: string, teamId: string, role: 'seeker' | 'hider') {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/teams/${teamId}/role`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ role }),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Failed to update team role');
    }

    return response.json();
  }

  async startNextRound(gameId: string) {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/next-round`, {
      method: 'POST',
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Failed to start next round');
    }

    return response.json();
  }

  // Location endpoints
  async updateLocation(teamId: string, latitude: number, longitude: number, gameId?: string) {
    try {
      console.log(`Updating location for team ${teamId}: ${latitude}, ${longitude}${gameId ? ` (game: ${gameId})` : ''}`);

      const response = await fetch(`${API_BASE_URL}/location/update`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ teamId, latitude, longitude, gameId }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP ${response.status}: ${errorText}`);
      }
      // Backend returns plain text, not JSON
      const result = await response.text();
      console.log('Location updated successfully:', result);
      return result;
    } catch (error) {
      console.error('Location update failed:', error);
      throw new Error('Failed to update location');
    }
  }

  async getDistance(seekerLat: number, seekerLon: number, hiderTeamId: string) {
    const response = await fetch(`${API_BASE_URL}/location/distance`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ seekerLat, seekerLon, hiderTeamId }),
    });

    if (!response.ok) {
      throw new Error('Failed to calculate distance');
    }

    return response.json();
  }

  // Push notifications
  async registerPushToken(gameId: string, teamId: string, token: string) {
    const response = await fetch(`${API_BASE_URL}/push/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameId, teamId, token }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to register push token');
      } catch (_) {
        throw new Error('Failed to register push token');
      }
    }
    return response.json();
  }

  async unregisterPushToken(gameId: string, teamId: string, token: string) {
    const response = await fetch(`${API_BASE_URL}/push/unregister`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameId, teamId, token }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to unregister push token');
      } catch (_) {
        throw new Error('Failed to unregister push token');
      }
    }
    return response.json();
  }

  async unregisterDeviceFromAllTeams(token: string) {
    const response = await fetch(`${API_BASE_URL}/push/unregister-device`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to unregister device');
      } catch (_) {
        throw new Error('Failed to unregister device');
      }
    }
    return response.json();
  }

  // Test notification function
  async sendTestNotification(gameId: string, teamId: string) {
    const response = await fetch(`${API_BASE_URL}/push/test`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameId, teamId }),
    });
    if (!response.ok) {
      try {
        const err = await response.json();
        throw new Error(err?.error || 'Failed to send test notification');
      } catch (_) {
        throw new Error('Failed to send test notification');
      }
    }
    return response.json();
  }
}

export default new ApiService();
