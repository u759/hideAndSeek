import { Router, Request, Response } from 'express';
import { ClueType, Clue, UBCLocation } from '../types';
import { generateClueContent } from '../services/clueGenerator';

const router = Router();

// Available clue types with their costs
const clueTypes: ClueType[] = [
  {
    id: 'exact-location',
    name: 'Exact Location',
    description: 'Exact current location on map',
    cost: 10
  },
  {
    id: 'team-selfie',
    name: 'Team Selfie',
    description: 'Selfie of whole team at arm\'s length including surroundings',
    cost: 8
  },
  {
    id: 'nearest-building',
    name: 'Nearest Building',
    description: 'Picture of nearest building (or interior of current building)',
    cost: 7
  },
  {
    id: 'tallest-building',
    name: 'Tallest Building View',
    description: 'Picture of tallest building you can see',
    cost: 6
  },
  {
    id: 'relative-direction',
    name: 'Relative Direction',
    description: 'Relative direction to Seekers',
    cost: 5
  },
  {
    id: 'distance',
    name: 'Distance',
    description: 'Distance from Seekers (in Google Maps walking time)',
    cost: 5
  },
  {
    id: 'inside-outside',
    name: 'Inside/Outside',
    description: 'Are you inside or outside a building?',
    cost: 3
  },
  {
    id: 'closest-street',
    name: 'Closest Street',
    description: 'Name of closest named street',
    cost: 4
  },
  {
    id: 'closest-landmark',
    name: 'Closest Landmark',
    description: 'Name of closest landmark',
    cost: 4
  },
  {
    id: 'closest-library',
    name: 'Closest Library',
    description: 'Name of closest library',
    cost: 4
  },
  {
    id: 'closest-museum',
    name: 'Closest Museum',
    description: 'Name of closest museum/gallery',
    cost: 4
  },
  {
    id: 'closest-parking',
    name: 'Closest Parking',
    description: 'Name of closest parking lot',
    cost: 3
  }
];

// Get all available clue types
router.get('/types', (req: Request, res: Response) => {
  res.json(clueTypes);
});

// Purchase a clue
router.post('/purchase', async (req: Request, res: Response) => {
  const { clueTypeId, gameId, purchasingTeamId } = req.body;
  
  const clueType = clueTypes.find(ct => ct.id === clueTypeId);
  if (!clueType) {
    return res.status(404).json({ error: 'Clue type not found' });
  }
  
  // Here you would:
  // 1. Check if team has enough tokens
  // 2. Get random hider team that hasn't been found
  // 3. Get their current location
  // 4. Generate clue content based on location and clue type
  
  try {
    // For demo purposes, using mock data
    const mockLocation = {
      latitude: 49.2606,
      longitude: -123.2460
    };
    
    const clueContent = await generateClueContent(clueType, mockLocation);
    
    const clue: Clue = {
      id: `clue_${Date.now()}`,
      type: clueType,
      content: clueContent,
      targetTeamId: 'mock_team_id', // Would be actual random hider team
      purchasedBy: purchasingTeamId,
      timestamp: Date.now()
    };
    
    res.json({
      clue,
      tokensSpent: clueType.cost,
      message: `Clue purchased for ${clueType.cost} tokens`
    });
  } catch (error) {
    console.error('Error generating clue:', error);
    res.status(500).json({ error: 'Failed to generate clue' });
  }
});

// Get clue history for a game
router.get('/:gameId/history', (req: Request, res: Response) => {
  // In a real app, this would fetch from database
  res.json([]);
});

export default router;
