import { Game, Team, DrawnCard, ClueType, Clue, Location } from '../types';
import { API_BASE_URL } from '../config/api';

class ApiService {
  // Game endpoints
  async createGame(teamNames: string[]): Promise<Game> {
    try {
      console.log(`Making request to: ${API_BASE_URL}/game`);
      const response = await fetch(`${API_BASE_URL}/game`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ teamNames }),
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to create game: ${response.status} - ${errorText}`);
      }
      
      return response.json();
    } catch (error) {
      console.error('Network error in createGame:', error);
      throw new Error(`Failed to create game: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  async createGameWithRole(teamNames: string[], playerRole: 'seeker' | 'hider'): Promise<Game> {
    try {
      console.log(`Making request to: ${API_BASE_URL}/game`);
      const response = await fetch(`${API_BASE_URL}/game`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ teamNames, playerRole }),
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to create game: ${response.status} - ${errorText}`);
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
      throw new Error('Failed to start game');
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
      throw new Error('Failed to resume game');
    }
    
    return response.json();
  }

  async nextRound(gameId: string): Promise<Game> {
    const response = await fetch(`${API_BASE_URL}/game/${gameId}/next-round`, {
      method: 'POST',
    });
    
    if (!response.ok) {
      throw new Error('Failed to start next round');
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
      throw new Error('Failed to draw card');
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

  // Clue endpoints
  async getClueTypes(): Promise<ClueType[]> {
    const response = await fetch(`${API_BASE_URL}/clues/types`);
    
    if (!response.ok) {
      throw new Error('Failed to fetch clue types');
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
      throw new Error('Failed to purchase clue');
    }
    
    return response.json();
  }

  async getClueHistory(gameId: string): Promise<Clue[]> {
    const response = await fetch(`${API_BASE_URL}/clues/${gameId}/history`);
    
    if (!response.ok) {
      throw new Error('Failed to fetch clue history');
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
}

export default new ApiService();
