import { ClueType, Location, UBCLocation } from '../types';

// UBC landmarks and buildings database
const ubcLocations: UBCLocation[] = [
  {
    name: "Irving K. Barber Learning Centre",
    type: "library",
    coordinates: { latitude: 49.2676, longitude: -123.2534 },
    description: "Main library with 24/7 study spaces"
  },
  {
    name: "Buchanan Building",
    type: "building",
    coordinates: { latitude: 49.2695, longitude: -123.2544 },
    description: "Home to Arts and Humanities departments"
  },
  {
    name: "Student Union Building (SUB)",
    type: "landmark",
    coordinates: { latitude: 49.2663, longitude: -123.2492 },
    description: "Student services and food court"
  },
  {
    name: "Koerner Library",
    type: "library",
    coordinates: { latitude: 49.2681, longitude: -123.2561 },
    description: "Humanities and Social Sciences library"
  },
  {
    name: "Chemistry Building",
    type: "building",
    coordinates: { latitude: 49.2625, longitude: -123.2529 },
    description: "Known for chemistry research and labs"
  },
  {
    name: "Physics & Astronomy Building",
    type: "building",
    coordinates: { latitude: 49.2623, longitude: -123.2525 },
    description: "Known for physics research and the TRIUMF facility"
  },
  {
    name: "Museum of Anthropology",
    type: "museum",
    coordinates: { latitude: 49.2690, longitude: -123.2591 },
    description: "World-renowned anthropology museum"
  },
  {
    name: "Rose Garden",
    type: "landmark",
    coordinates: { latitude: 49.2676, longitude: -123.2576 },
    description: "Beautiful garden with seasonal flowers"
  },
  {
    name: "Main Mall",
    type: "street",
    coordinates: { latitude: 49.2665, longitude: -123.2520 },
    description: "Central pedestrian thoroughfare"
  },
  {
    name: "East Mall",
    type: "street",
    coordinates: { latitude: 49.2660, longitude: -123.2500 },
    description: "Eastern campus road"
  }
];

// Calculate distance between two coordinates
function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000; // Earth's radius in meters
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

// Find closest location of a specific type
function findClosestLocation(location: Location, type: string): UBCLocation | null {
  const filtered = ubcLocations.filter(loc => loc.type === type);
  if (filtered.length === 0) return null;
  
  let closest = filtered[0];
  let minDistance = calculateDistance(
    location.latitude, location.longitude,
    closest.coordinates.latitude, closest.coordinates.longitude
  );
  
  for (const loc of filtered.slice(1)) {
    const distance = calculateDistance(
      location.latitude, location.longitude,
      loc.coordinates.latitude, loc.coordinates.longitude
    );
    if (distance < minDistance) {
      minDistance = distance;
      closest = loc;
    }
  }
  
  return closest;
}

// Generate AI-powered clue using location context
async function generateAIClue(location: Location, clueType: ClueType): Promise<string> {
  // Find nearest building for context
  const nearestBuilding = findClosestLocation(location, 'building');
  const nearestLandmark = findClosestLocation(location, 'landmark');
  
  const context = nearestBuilding?.description || 
                 nearestLandmark?.description || 
                 "general campus activities";
  
  // For demo purposes, return a contextual clue
  // In production, this would call OpenAI API
  const clues = [
    `Near a place where ${context.toLowerCase()}`,
    `Close to an area known for ${context.toLowerCase()}`,
    `In the vicinity of ${nearestBuilding?.name || nearestLandmark?.name || 'a campus building'}`,
    `Within walking distance of facilities that ${context.toLowerCase()}`
  ];
  
  return clues[Math.floor(Math.random() * clues.length)];
}

export async function generateClueContent(clueType: ClueType, location: Location): Promise<string> {
  switch (clueType.id) {
    case 'exact-location':
      return `Coordinates: ${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`;
    
    case 'inside-outside':
      // Simple heuristic: if very close to a building, probably inside
      const nearestBuilding = findClosestLocation(location, 'building');
      if (nearestBuilding) {
        const distance = calculateDistance(
          location.latitude, location.longitude,
          nearestBuilding.coordinates.latitude, nearestBuilding.coordinates.longitude
        );
        return distance < 50 ? "Inside a building" : "Outside";
      }
      return "Outside";
    
    case 'closest-street':
      const closestStreet = findClosestLocation(location, 'street');
      return closestStreet ? `Closest street: ${closestStreet.name}` : "No nearby named streets identified";
    
    case 'closest-landmark':
      const closestLandmark = findClosestLocation(location, 'landmark');
      return closestLandmark ? `Closest landmark: ${closestLandmark.name}` : "No nearby landmarks identified";
    
    case 'closest-library':
      const closestLibrary = findClosestLocation(location, 'library');
      return closestLibrary ? `Closest library: ${closestLibrary.name}` : "No nearby libraries identified";
    
    case 'closest-museum':
      const closestMuseum = findClosestLocation(location, 'museum');
      return closestMuseum ? `Closest museum: ${closestMuseum.name}` : "No nearby museums identified";
    
    case 'closest-parking':
      return "Parking information: Check UBC parking map for nearest lot";
    
    case 'team-selfie':
      return "Request: Send a selfie of your whole team at arm's length including surroundings";
    
    case 'nearest-building':
      return "Request: Send a picture of the nearest building (or interior if you're inside)";
    
    case 'tallest-building':
      return "Request: Send a picture of the tallest building you can see from your location";
    
    case 'relative-direction':
      return "Direction clue will be calculated based on seeker position";
    
    case 'distance':
      return "Distance clue will be calculated based on seeker position";
    
    default:
      return await generateAIClue(location, clueType);
  }
}
