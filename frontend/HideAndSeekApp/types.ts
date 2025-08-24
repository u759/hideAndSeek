export interface Challenge {
  id: string;
  title: string;
  description: string;
  token_count: number | string | null;
}

export interface Curse {
  id: string;
  title: string;
  description: string;
  token_count: number | string | null;
  time_seconds?: number;
  penalty?: number;
}

export interface Team {
  id: string;
  name: string;
  role: 'seeker' | 'hider';
  tokens: number;
  location?: {
    latitude: number;
    longitude: number;
    timestamp: number;
  };
  completedChallenges: string[];
  activeCurses: ActiveCurse[];
  appliedCurses: AppliedCurse[];
  vetoEndTime?: number;
  totalHiderTime?: number;
  // Server-side active challenge (must be vetoed or completed)
  activeChallenge?: ActiveChallenge;
}

export interface ActiveCurse {
  curse: Curse;
  startTime: number;
  endTime: number;
  completed?: boolean;
  completedAt?: number;
  acknowledged?: boolean;
}

export interface AppliedCurse {
  curse: Curse;
  targetTeamId: string;
  targetTeamName: string;
  startTime: number;
  endTime: number;
}

export interface Game {
  id: string;
  code: string;
  teams: Team[];
  startTime: number;
  pauseTime?: number;
  totalPausedDuration?: number;
  endTime?: number;
  round: number;
  status: 'waiting' | 'active' | 'paused' | 'ended';
  roundLengthMinutes?: number; // Round length in minutes (null = no time limit)
  pausedByTimeLimit?: boolean; // True if paused due to round time limit
  // New timing fields
  gameStartTime?: number; // First time the game became active
  roundStartTime?: number; // Start time of the current round
  pausedDurationAtRoundStart?: number; // Snapshot of paused duration at round start
  // Computed durations (milliseconds), exclude paused time
  gameDuration?: number;
  roundDuration?: number;
}

// Enhanced stats returned from GET /api/game/{gameId}/stats
export interface GameStatsTeam {
  id: string;
  name: string;
  role: 'seeker' | 'hider';
  tokens: number;
  totalHiderTime: number;
  totalHiderTimeFormatted: string;
  // Optional to allow backward compatibility if server hasn't populated yet
  completedChallengesCount?: number;
  cursesAppliedCount?: number;
}

export interface GameStatsWinner {
  id: string;
  name: string;
  totalHiderTime: number;
  totalHiderTimeFormatted: string;
}

export interface Leaderboard {
  byHiderTime: Array<{
    id: string;
    name: string;
    totalHiderTime: number;
    totalHiderTimeFormatted: string;
  }>;
  byChallengesCompleted: Array<{
    id: string;
    name: string;
    completedChallengesCount: number;
  }>;
  byCursesApplied: Array<{
    id: string;
    name: string;
    cursesAppliedCount: number;
  }>;
}

export interface GameStats {
  gameId: string;
  round: number;
  status: Game['status'];
  teams: GameStatsTeam[];
  winner?: GameStatsWinner;
  leaderboard?: Leaderboard;
}

export interface ClueType {
  id: string;
  name: string;
  description: string;
  cost: number;
  range?: number; // Range in meters, null/undefined = unlimited
}

export interface HiderClueData {
  teamId: string;
  teamName: string;
  latitude?: number;
  longitude?: number;
  distance?: number;
  direction?: string;
  additionalData?: string; // For selfie URLs, landmark names, etc.
}

export interface Clue {
  id: string;
  text: string;
  cost: number;
  timestamp: number;
  clueTypeId?: string;
  responseType?: string;
  targetHiderTeamId?: string; // Deprecated, use targetHiderTeamIds
  targetHiderTeamIds?: string[]; // Multiple hider teams
  hiderData?: HiderClueData[]; // Aggregated data for multiple hiders
  location?: {
    latitude: number;
    longitude: number;
    teamName: string;
  };
}

export interface DrawnCard {
  card: Challenge | Curse;
  type: 'challenge' | 'curse';
  remainingCards: number;
}

export interface Location {
  latitude: number;
  longitude: number;
}

export interface ActiveChallenge {
  challenge: Challenge;
  startTime: number;
  completed: boolean;
}

// Navigation types
export type RootStackParamList = {
  Home: undefined;
  RoleSelection: undefined;
  GameSetup: undefined;
  GameLobby: { gameId: string; gameCode: string };
  TeamJoin: { gameId: string; gameCode: string };
  JoinGame: undefined;
  Game: { gameId: string; teamId: string };
  Challenges: { gameId: string; teamId: string };
  Clues: { gameId: string; teamId: string };
  Location: { gameId: string; teamId: string };
};

export type TabParamList = {
  Overview: undefined;
  Challenges: undefined;
  Curses: undefined;
  Clues: undefined;
  Location: undefined;
  FindHiders: undefined;
};
