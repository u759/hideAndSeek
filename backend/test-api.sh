# API Testing Script

# Test the backend API endpoints

# Health check
curl http://localhost:3000/health

# Create a new game
curl -X POST http://localhost:3000/api/game \
  -H "Content-Type: application/json" \
  -d '{
    "teamNames": ["Ayden & Brendan", "Leo & Simon", "Ryan & Kevin", "Tom & Nick"]
  }'

# Get challenges and curses
curl http://localhost:3000/api/challenges

# Get clue types
curl http://localhost:3000/api/clues/types

echo "API tests completed!"
