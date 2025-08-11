import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import dotenv from 'dotenv';
import { createServer } from 'http';
import { WebSocketServer } from 'ws';

import gameRoutes from './routes/game';
import challengeRoutes from './routes/challenges';
import clueRoutes from './routes/clues';
import locationRoutes from './routes/location';
import { setupWebSocket } from './websocket';

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

// Middleware
app.use(helmet());
app.use(cors());
app.use(morgan('combined'));
app.use(express.json());

// Routes
app.use('/api/game', gameRoutes);
app.use('/api/challenges', challengeRoutes);
app.use('/api/clues', clueRoutes);
app.use('/api/location', locationRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// Create HTTP server
const server = createServer(app);

// Setup WebSocket
const wss = new WebSocketServer({ server });
setupWebSocket(wss);

server.listen(Number(port), '0.0.0.0', () => {
  console.log(`🚀 Server running on port ${port}`);
  console.log(`📱 WebSocket server ready for real-time updates`);
  console.log(`🌐 Server accessible at http://192.168.1.147:${port}`);
});
