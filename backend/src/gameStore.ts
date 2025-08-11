import { Game } from './types';

// Shared in-memory storage for games
export let games: Game[] = [];

export const addGame = (game: Game) => {
  games.push(game);
};

export const findGame = (gameId: string) => {
  return games.find(g => g.id === gameId);
};

export const updateGame = (gameId: string, updates: Partial<Game>) => {
  const gameIndex = games.findIndex(g => g.id === gameId);
  if (gameIndex !== -1) {
    games[gameIndex] = { ...games[gameIndex], ...updates };
    return games[gameIndex];
  }
  return null;
};
