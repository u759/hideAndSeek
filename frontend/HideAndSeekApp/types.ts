export interface Challenge {
  title: string;
  description: string;
  token_count: number | string | null;
}

export interface Curse {
  title: string;
  description: string;
  token_count: number | string | null;
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
  vetoEndTime?: number;
}

export interface ActiveCurse {
  curse: Curse;
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
  round: number;
  status: 'waiting' | 'active' | 'paused' | 'ended';
}

export interface ClueType {
  id: string;
  name: string;
  description: string;
  cost: number;
}

export interface Clue {
  id: string;
  type: ClueType;
  content: string;
  targetTeamId: string;
  purchasedBy: string;
  timestamp: number;
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
  Clues: undefined;
  Location: undefined;
  FindHiders: undefined;
};
