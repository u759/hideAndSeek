# UBCeek: Hide and Seek

A React Native app with TypeScript backend for playing hide and seek across UBC campus.

## Project Structure

```
hideandseek/
├── backend/                 # Node.js TypeScript backend
│   ├── src/
│   │   ├── routes/         # API routes
│   │   ├── services/       # Business logic
│   │   └── types.ts        # TypeScript interfaces
│   └── package.json
├── frontend/               # React Native Expo app
│   └── HideAndSeekApp/
│       ├── screens/        # App screens
│       ├── components/     # Reusable components
│       ├── services/       # API client
│       └── types.ts        # TypeScript interfaces
└── challenges_curses.json  # Game data
```

## Features

### Backend API
- **Game Management**: Create games, manage teams, track rounds
- **Challenge System**: Random card drawing, completion tracking, veto penalties
- **Location Services**: Real-time hider location tracking
- **Clue Generation**: AI-powered location-based clues for UBC campus
- **WebSocket Support**: Real-time game updates

### Frontend App
- **Game Setup**: Create games with multiple teams
- **Role-based UI**: Different interfaces for seekers vs hiders
- **Challenge Cards**: Draw and complete challenges to earn tokens
- **Clue System**: Purchase location-based clues about hiders
- **Location Tracking**: Automatic GPS tracking for hider teams
- **Real-time Updates**: Live game state synchronization

## Getting Started

### Backend Setup

1. Navigate to backend directory:
```bash
cd backend
```

2. Install dependencies:
```bash
npm install
```

3. Set up environment variables:
```bash
cp .env.example .env
# Edit .env with your configuration
```

4. Start development server:
```bash
npm run dev
```

The backend will run on http://localhost:3000

### Frontend Setup

1. Navigate to frontend directory:
```bash
cd frontend/HideAndSeekApp
```

2. Install dependencies:
```bash
npm install
```

3. Start Expo development server:
```bash
npm start
```

4. Use Expo Go app to test on your device, or:
   - `npm run android` for Android emulator
   - `npm run web` for web browser testing

## Game Rules

### Setup
- 2-6 teams can play
- First team starts as Seekers, others as Hiders
- Hiders get 10-minute head start to hide on UBC campus

### Gameplay
- **Seekers**: Draw challenge cards to earn tokens, use tokens to buy clues
- **Hiders**: Share location automatically, try to stay hidden
- **Challenges**: Complete for tokens or veto with 5-minute penalty
- **Clues**: Purchase hints about hider locations using tokens
- **Rounds**: When all hiders found, roles rotate

### Restrictions
- Hiders cannot run (walking only)
- No hiding in washrooms or restricted areas
- Must stay within UBC campus boundaries
- No transit allowed during rounds

## API Endpoints

### Game Management
- `POST /api/game` - Create new game
- `GET /api/game/:id` - Get game state
- `PATCH /api/game/:id/status` - Update game status
- `POST /api/game/:id/next-round` - Start next round

### Challenges
- `GET /api/challenges` - Get all challenges/curses
- `POST /api/challenges/draw` - Draw random card
- `POST /api/challenges/complete` - Complete challenge
- `POST /api/challenges/veto` - Veto challenge

### Clues
- `GET /api/clues/types` - Get available clue types
- `POST /api/clues/purchase` - Purchase clue
- `GET /api/clues/:gameId/history` - Get clue history

### Location
- `POST /api/location/update` - Update hider location
- `POST /api/location/distance` - Calculate distance between teams

## Technologies Used

### Backend
- Node.js with TypeScript
Admin Panel (built into WAR)

- Set admin password via one of:
   - Environment variable ADMIN_PASSWORD
   - .env file in project root containing: ADMIN_PASSWORD=yourSecret
   - application.properties: admin.password=yourSecret

- Run backend and open /admin (redirects to /admin-login.html). After login, you'll be redirected to /admin-dashboard.html.
- All admin API endpoints are protected via session; direct API access without login returns 401.
- WebSocket for real-time updates
- OpenAI integration for clue generation

### Frontend
- React Native with Expo
- TypeScript for type safety
- React Navigation for screen management
- Expo Location for GPS tracking
- Vector Icons for UI elements

## Development Notes

- Backend uses in-memory storage (replace with database in production)
- Location tracking optimized for UBC campus bounds
- Challenge cards include various difficulty levels and token rewards
- Clue generation uses UBC-specific landmarks and buildings
- Real-time updates via WebSocket for synchronized gameplay

## Future Enhancements

- Database integration (PostgreSQL/MongoDB)
- User authentication and profiles
- Game history and statistics
- Custom challenge creation
- Photo submission for challenges
- Enhanced AI clue generation
- Multi-campus support