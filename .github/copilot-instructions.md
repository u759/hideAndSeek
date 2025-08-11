# UBCeek Hide and Seek - AI Coding Assistant Instructions

## Project Architecture

This is a **real-time multiplayer location-based game** with React Native frontend and Node.js backend. Teams alternate between "seeker" and "hider" roles, using challenges, tokens, and location clues.

### Core Components
- **Backend** (`backend/src/`): Express API with WebSocket, in-memory game state, OpenAI integration
- **Frontend** (`frontend/HideAndSeekApp/`): Expo React Native app with role-based tab navigation
- **Game Data** (`backend/challenges_curses.json`): Challenge cards with token rewards/penalties

## Key Architectural Patterns

### In-Memory State Management
- **NO DATABASE**: Uses `backend/src/gameStore.ts` for shared state across API routes
- Game state persists only during server runtime - restart clears all games
- Always import game operations from `gameStore.ts`, never manipulate `games` array directly

### Type Synchronization
- **Shared Types**: `backend/src/types.ts` and `frontend/HideAndSeekApp/types.ts` must stay synchronized
- Core interfaces: `Game`, `Team`, `Challenge`, `Curse`, `Clue`, `Location`
- When adding fields, update BOTH type files identically

### Real-Time Communication
- **WebSocket** (`backend/src/websocket.ts`): Broadcasts game state changes to connected clients
- **Pattern**: API route updates → broadcast via `broadcastToGame()` → frontend receives updates
- Location updates happen every 30 seconds for hider teams via `useLocationTracker` hook

### Role-Based UI Navigation
- **Dynamic Tabs** (`frontend/HideAndSeekApp/screens/GameScreen.tsx`): Shows different tabs based on team role
- Seekers: Overview, Challenges, Clues, Find Hiders
- Hiders: Overview, Challenges, Location
- Tab visibility controlled by `currentTeam?.role === 'seeker'` checks

## Critical Development Workflows

### Backend Development
```bash
cd backend
npm run dev  # Starts nodemon with hot reload on :3000
```

### Frontend Development
```bash
cd frontend/HideAndSeekApp
npm start    # Starts Expo dev server
npm run web  # Quick testing in browser
```

### API Testing
- Use `backend/test-api.sh` for endpoint testing
- Or PowerShell: `Invoke-RestMethod -Uri "http://localhost:3000/api/game" -Method POST -ContentType "application/json" -Body '{"teamNames":["Team1"]}'`

## Project-Specific Conventions

### Game Flow Implementation
- **Dynamic Role Switching**: When hiders are found, they become seekers (`/api/game/:gameId/teams/:hiderId/found`)
- **Token Economy**: Complete challenges → earn tokens → buy clues about hider locations
- **Veto System**: Refuse challenge → 5-minute penalty → can't draw new cards during penalty

### Location Handling
- **UBC Campus Specific**: Clue generation uses UBC landmarks (`backend/src/services/clueGenerator.ts`)
- **Frontend Validation**: Campus boundary checks happen in React Native, backend accepts all coordinates
- **Automatic Tracking**: Hiders share location every 30s when game is active

### Challenge System
- **Card Drawing**: Random selection from `challenges_curses.json` weighted by difficulty
- **Completion Tracking**: `completedChallenges` array prevents re-drawing same card
- **Active Curses**: Time-based penalties that affect team capabilities

## Integration Points

### OpenAI Integration
- **Environment**: Requires `OPENAI_API_KEY` in `.env`
- **Clue Generation**: `/api/clues/purchase` generates location-based hints using OpenAI
- **Context**: Uses UBC-specific landmarks and current hider locations

### Expo Location Services
- **Permissions**: Handled in `useLocationTracker` hook with user prompts
- **Background Tracking**: Continuous GPS for hider teams during active games
- **Error Handling**: Graceful fallback when location services unavailable

### API Configuration
- **Base URL**: Set in `frontend/HideAndSeekApp/config/api.ts`
- **Default**: `http://192.168.1.147:3000` (update for your local network)
- **CORS**: Backend configured for cross-origin requests

## Common Patterns

### Error Handling
- Backend: Return proper HTTP status codes with descriptive messages
- Frontend: Use try/catch blocks with user-friendly Alert dialogs
- WebSocket: Always wrap message parsing in try/catch

### State Updates
- Always update game state via `gameStore.ts` methods
- Broadcast state changes to WebSocket clients after mutations
- Frontend polls `/api/game/:id` every 5 seconds for state sync

### Component Structure
- Screens handle navigation and data fetching
- Components receive data as props, avoid direct API calls
- Custom hooks (like `useLocationTracker`) manage complex state logic
