import { Router, Request, Response } from 'express';
import challengesData from '../../challenges_curses.json';
import { Challenge, Curse, Team } from '../types';
import { findGame } from '../gameStore';

const router = Router();

// Get all challenges and curses
router.get('/', (req: Request, res: Response) => {
  res.json(challengesData);
});

// Draw a random challenge/curse that hasn't been completed by the team
router.post('/draw', (req: Request, res: Response) => {
  const { teamId, gameId } = req.body;
  
  const game = findGame(gameId);
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }
  
  const team = game.teams.find(t => t.id === teamId);
  if (!team) {
    return res.status(404).json({ error: 'Team not found' });
  }
  
  // Check if team is under veto penalty
  if (team.vetoEndTime && Date.now() < team.vetoEndTime) {
    const remainingTime = Math.ceil((team.vetoEndTime - Date.now()) / 1000);
    return res.status(429).json({ 
      error: 'Cannot draw cards due to veto penalty',
      remainingTime,
      message: `You must wait ${Math.ceil(remainingTime / 60)} more minutes before drawing another card.`
    });
  }
  
  // Get team's completed challenges
  const completedChallenges = team.completedChallenges || [];
  
  // Combine challenges and curses
  const allCards = [
    ...challengesData.challenges,
    ...challengesData.curses
  ];
  
  // Filter out completed challenges
  const availableCards = allCards.filter(card => 
    !completedChallenges.includes(card.title)
  );
  
  if (availableCards.length === 0) {
    return res.status(400).json({ error: 'No more challenges available' });
  }
  
  // Draw random card
  const randomIndex = Math.floor(Math.random() * availableCards.length);
  const drawnCard = availableCards[randomIndex];
  
  // Determine if it's a challenge or curse
  const isChallenge = challengesData.challenges.some(c => c.title === drawnCard.title);
  
  res.json({
    card: drawnCard,
    type: isChallenge ? 'challenge' : 'curse',
    remainingCards: availableCards.length - 1
  });
});

// Complete a challenge
router.post('/complete', (req: Request, res: Response) => {
  const { challengeTitle, teamId, gameId } = req.body;
  
  const game = findGame(gameId);
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }
  
  const team = game.teams.find(t => t.id === teamId);
  if (!team) {
    return res.status(404).json({ error: 'Team not found' });
  }
  
  const challenge = challengesData.challenges.find(c => c.title === challengeTitle);
  
  if (!challenge) {
    return res.status(404).json({ error: 'Challenge not found' });
  }
  
  // Calculate tokens earned
  let tokensEarned = 0;
  if (typeof challenge.token_count === 'number') {
    tokensEarned = challenge.token_count;
  } else if (challenge.token_count === 'Variable') {
    // Handle variable token challenges - could be dice roll based
    tokensEarned = Math.floor(Math.random() * 6) + 1; // 1-6 tokens for variable
  } else if (typeof challenge.token_count === 'string' && challenge.token_count.includes('x')) {
    // Handle dice roll multipliers like "2 x Dice roll"
    const diceRoll = Math.floor(Math.random() * 6) + 1;
    const multiplier = parseInt(challenge.token_count.split('x')[0].trim()) || 2;
    tokensEarned = multiplier * diceRoll;
  }
  
  // Update team tokens and completed challenges
  team.tokens += tokensEarned;
  if (!team.completedChallenges.includes(challengeTitle)) {
    team.completedChallenges.push(challengeTitle);
  }
  
  res.json({
    tokensEarned,
    newTokenBalance: team.tokens,
    challenge,
    message: `Challenge completed! Earned ${tokensEarned} tokens.`
  });
});

// Veto a challenge
router.post('/veto', (req: Request, res: Response) => {
  const { challengeTitle, teamId, gameId } = req.body;
  
  const game = findGame(gameId);
  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }
  
  const team = game.teams.find(t => t.id === teamId);
  if (!team) {
    return res.status(404).json({ error: 'Team not found' });
  }
  
  // Set 5-minute veto penalty
  const vetoEndTime = Date.now() + (5 * 60 * 1000); // 5 minutes from now
  team.vetoEndTime = vetoEndTime;
  
  res.json({
    message: 'Challenge vetoed. 5-minute penalty applied.',
    vetoEndTime,
    penaltyDuration: 5 * 60 * 1000 // 5 minutes in milliseconds
  });
});

export default router;
