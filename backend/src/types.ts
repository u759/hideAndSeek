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
  vetoEndTime?: number; // timestamp when veto penalty ends
}

export interface ActiveCurse {
  curse: Curse;
  startTime: number;
  endTime: number;
}

export interface Game {
  id: string;
  code: string; // 6-letter game code
  teams: Team[];
  startTime: number;
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

export interface Location {
  latitude: number;
  longitude: number;
}

export interface UBCLocation {
  name: string;
  type: 'building' | 'landmark' | 'library' | 'museum' | 'parking' | 'street';
  coordinates: Location;
  description?: string;
}
