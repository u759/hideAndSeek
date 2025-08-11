import { Router, Request, Response } from 'express';
import { Location } from '../types';
import { findGame } from '../gameStore';

const router = Router();

// Store hider locations (in production, use a database)
let hiderLocations: Map<string, { location: Location; timestamp: number }> = new Map();

// Update hider location
router.post('/update', (req: Request, res: Response) => {
  const { teamId, gameId, latitude, longitude } = req.body;
  
  if (!teamId || typeof latitude !== 'number' || typeof longitude !== 'number') {
    return res.status(400).json({ error: 'Invalid location data' });
  }

  // If gameId is provided, find the game and team
  if (gameId) {
    const game = findGame(gameId);
    if (!game) {
      return res.status(404).json({ error: 'Game not found' });
    }

    const team = game.teams.find(t => t.id === teamId);
    if (!team) {
      return res.status(404).json({ error: 'Team not found' });
    }
    
    // Update team location in game state
    team.location = {
      latitude,
      longitude,
      timestamp: Date.now()
    };
  }

  // Always store in the hiderLocations map for backwards compatibility
  hiderLocations.set(teamId, {
    location: { latitude, longitude },
    timestamp: Date.now()
  });
  
  res.json({
    message: 'Location updated successfully',
    timestamp: Date.now()
  });
});

// Get hider locations (for clue generation)
router.get('/hiders', (req: Request, res: Response) => {
  const locations = Array.from(hiderLocations.entries()).map(([teamId, data]) => ({
    teamId,
    ...data
  }));
  
  res.json(locations);
});

// Get distance between two points (Haversine formula)
function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371; // Earth's radius in kilometers
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

// Calculate distance between seeker and hider
router.post('/distance', (req: Request, res: Response) => {
  const { seekerLat, seekerLon, hiderTeamId } = req.body;
  
  const hiderData = hiderLocations.get(hiderTeamId);
  if (!hiderData) {
    return res.status(404).json({ error: 'Hider location not found' });
  }
  
  const distance = calculateDistance(
    seekerLat, seekerLon,
    hiderData.location.latitude, hiderData.location.longitude
  );
  
  // Convert to approximate walking time (assuming 5 km/h walking speed)
  const walkingTimeMinutes = Math.round((distance / 5) * 60);
  
  res.json({
    distanceKm: Math.round(distance * 100) / 100,
    walkingTimeMinutes,
    walkingTimeText: walkingTimeMinutes < 60 
      ? `${walkingTimeMinutes} minutes`
      : `${Math.floor(walkingTimeMinutes / 60)}h ${walkingTimeMinutes % 60}m`
  });
});

export default router;
