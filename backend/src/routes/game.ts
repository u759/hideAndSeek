import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { Game, Team } from '../types';
import { games, addGame, findGame } from '../gameStore';

const router = Router();

// Generate a unique 6-letter game code
const generateGameCode = (): string => {
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let code = '';
  do {
    code = '';
    for (let i = 0; i < 6; i++) {
      code += letters.charAt(Math.floor(Math.random() * letters.length));
    }
  } while (games.some(g => g.code === code)); // Ensure uniqueness
  return code;
};

// Create a new game
router.post('/', (req: Request, res: Response) => {
  const { teamNames, playerRole } = req.body;
  
  let teams: Team[] = [];
  
  if (playerRole && teamNames && teamNames.length === 1) {
    // Single player game
    teams = [{
      id: uuidv4(),
      name: teamNames[0],
      role: playerRole,
      tokens: 0,
      completedChallenges: [],
      activeCurses: [],
      vetoEndTime: undefined
    }];
  } else if (teamNames && Array.isArray(teamNames) && teamNames.length >= 2) {
    // Multi-team game
    teams = teamNames.map((name: string, index: number) => ({
      id: uuidv4(),
      name,
      role: index === 0 ? 'seeker' : 'hider',
      tokens: 0,
      completedChallenges: [],
      activeCurses: [],
      vetoEndTime: undefined
    }));
  } else {
    return res.status(400).json({ error: 'Invalid team configuration' });
  }

  const game: Game = {
    id: uuidv4(),
    code: generateGameCode(),
    teams,
    startTime: Date.now(),
    round: 1,
    status: 'waiting'
  };

  addGame(game);
  res.json(game);
});

// Get game by ID
router.get('/:gameId', (req: Request, res: Response) => {
  const game = findGame(req.params.gameId);
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }
  res.json(game);
});

// Get game by code
router.get('/code/:gameCode', (req: Request, res: Response) => {
  const gameCode = req.params.gameCode.toUpperCase();
  const game = games.find(g => g.code === gameCode);
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }
  res.json(game);
});

// Update game status
router.patch('/:gameId/status', (req: Request, res: Response) => {
  const { status } = req.body;
  const game = findGame(req.params.gameId);
  
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  game.status = status;
  res.json(game);
});

// Switch team roles (new round)
router.post('/:gameId/next-round', (req: Request, res: Response) => {
  const game = findGame(req.params.gameId);
  
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  // Rotate roles: current seeker becomes hider, next hider becomes seeker
  const currentSeeker = game.teams.find(t => t.role === 'seeker');
  const hiders = game.teams.filter(t => t.role === 'hider');
  
  if (currentSeeker && hiders.length > 0) {
    currentSeeker.role = 'hider';
    hiders[0].role = 'seeker';
    
    // Reset seeker's completed challenges and tokens for new round
    hiders[0].completedChallenges = [];
    hiders[0].tokens = 0;
  }

  game.round += 1;
  res.json(game);
});

// Update team tokens
router.patch('/:gameId/teams/:teamId/tokens', (req: Request, res: Response) => {
  const { tokens } = req.body;
  const game = findGame(req.params.gameId);
  
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  const team = game.teams.find(t => t.id === req.params.teamId);
  if (!team) {
    return res.status(404).json({ error: 'Team not found' });
  }

  team.tokens = tokens;
  res.json(team);
});

// Switch team role (when a hider is found)
router.patch('/:gameId/teams/:teamId/role', (req: Request, res: Response) => {
  const { role, foundByTeamId } = req.body;
  const game = findGame(req.params.gameId);
  
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  const team = game.teams.find(t => t.id === req.params.teamId);
  if (!team) {
    return res.status(404).json({ error: 'Team not found' });
  }

  const oldRole = team.role;
  team.role = role;

  // If a hider is being converted to seeker, reset their seeker-specific stats
  if (oldRole === 'hider' && role === 'seeker') {
    team.tokens = 0;
    team.completedChallenges = [];
    team.activeCurses = [];
    team.vetoEndTime = undefined;
  }

  // Check if game should end (only one hider remaining)
  const remainingHiders = game.teams.filter(t => t.role === 'hider');
  if (remainingHiders.length === 1) {
    game.status = 'ended';
  }

  res.json({
    team,
    game,
    message: `${team.name} is now a ${role}`,
    remainingHiders: remainingHiders.length
  });
});

// Mark a hider as found
router.post('/:gameId/teams/:hiderId/found', (req: Request, res: Response) => {
  const { seekerId } = req.body;
  const game = findGame(req.params.gameId);
  
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  const hider = game.teams.find(t => t.id === req.params.hiderId);
  const seeker = game.teams.find(t => t.id === seekerId);
  
  if (!hider || !seeker) {
    return res.status(404).json({ error: 'Team not found' });
  }

  if (hider.role !== 'hider') {
    return res.status(400).json({ error: 'Team is not a hider' });
  }

  if (seeker.role !== 'seeker') {
    return res.status(400).json({ error: 'Finding team is not a seeker' });
  }

  // Convert hider to seeker
  const oldRole = hider.role;
  hider.role = 'seeker';
  hider.tokens = 0;
  hider.completedChallenges = [];
  hider.activeCurses = [];
  hider.vetoEndTime = undefined;

  // Award points to the finding seeker team
  seeker.tokens += 50; // Bonus tokens for finding a hider

  // Check if game should end
  const remainingHiders = game.teams.filter(t => t.role === 'hider');
  if (remainingHiders.length === 1) {
    game.status = 'ended';
  }

  res.json({
    foundHider: hider,
    findingSeeker: seeker,
    game,
    message: `${hider.name} was found by ${seeker.name} and is now a seeker!`,
    remainingHiders: remainingHiders.length,
    gameEnded: game.status === 'ended'
  });
});

// Get game statistics
router.get('/:gameId/stats', (req: Request, res: Response) => {
  const game = findGame(req.params.gameId);
  
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  const seekers = game.teams.filter(t => t.role === 'seeker');
  const hiders = game.teams.filter(t => t.role === 'hider');

  res.json({
    totalTeams: game.teams.length,
    seekers: seekers.length,
    hiders: hiders.length,
    gameStatus: game.status,
    round: game.round,
    gameEnded: game.status === 'ended',
    winner: game.status === 'ended' && hiders.length === 1 ? hiders[0] : null
  });
});

export default router;
