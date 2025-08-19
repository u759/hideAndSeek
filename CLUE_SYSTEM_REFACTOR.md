# Refactored Clue System - Implementation Summary

## Overview
The clue system has been completely refactored to support the new clue types from `clue_types.json` with asynchronous manual responses from hider teams.

## New Clue Types Implemented

### 1. Exact Location (15 tokens)
- **Behavior**: Immediate response with precise GPS coordinates
- **Implementation**: Direct location sharing from closest hider team
- **Response Type**: Automatic with map integration support

### 2. Selfie (10 tokens) 
- **Behavior**: Async request requiring photo from hider team
- **Implementation**: 
  - Mandatory popup for hider team with camera access
  - 5-minute timeout for response
  - Photo upload via `/api/uploads/selfie` endpoint
  - WebSocket notification to requesting seeker team

### 3. Closest Landmark (8 tokens)
- **Behavior**: Async request requiring text response from hider team
- **Implementation**:
  - Mandatory popup with landmark type selection (street, library, museum, parking lot)
  - 5-minute timeout for response
  - Text response sent via `/api/clues/requests/{requestId}/respond`
  - WebSocket notification to requesting seeker team

### 4. Relative Direction (5 tokens)
- **Behavior**: Immediate compass direction from seeker to closest hider
- **Implementation**: Automatic calculation using haversine formula
- **Response Type**: Automatic

### 5. Distance from Seekers (5 tokens)
- **Behavior**: Immediate straight-line distance calculation
- **Implementation**: Automatic calculation in meters
- **Response Type**: Automatic

## Key Features

### Closest Hider Targeting
- All clues now target the closest hider team to the requesting seeker
- Requires seekers to have location services enabled
- Distance calculated using haversine formula

### Asynchronous Response System
- New models: `ClueRequest`, `ClueResponse`, `PurchasedClue` (enhanced)
- Real-time WebSocket notifications for pending requests and responses
- Automatic timeout handling (5 minutes for manual responses)
- Status tracking: "pending", "completed", "expired"

### File Upload Support
- Dedicated endpoint for selfie uploads
- Automatic file serving at `/api/uploads/files/{filename}`
- Configurable upload directory and file size limits

## New API Endpoints

### Clue Management
- `GET /api/clues/types` - Get available clue types
- `POST /api/clues/purchase` - Purchase a clue (enhanced)
- `GET /api/clues/{gameId}/teams/{teamId}/history` - Get clue history (enhanced)

### Async Clue Handling
- `GET /api/clues/{gameId}/teams/{teamId}/requests` - Get pending requests for hider team
- `POST /api/clues/requests/{requestId}/respond` - Respond to clue request (text)

### File Upload
- `POST /api/uploads/selfie` - Upload selfie photo response
- `GET /api/uploads/files/{filename}` - Retrieve uploaded files

## WebSocket Events

### For Hider Teams
- `clueRequest` - New clue request requiring response
  - Contains: requestId, clueType, description, expiration time

### For Seeker Teams  
- `clueResponse` - Response received for pending clue
  - Contains: responseData, timestamp, clue type

### For All Teams
- `gameUpdate` - General game state updates

## Database/Storage Changes

### GameStore Enhancements
- `clueRequests` - Map of pending/completed clue requests
- `clueResponses` - Map of responses by requesting team
- New methods for closest hider calculation and distance computation

### Enhanced Models
- `PurchasedClue` - Added status, requestId, responseType, targetHiderTeamId
- `ClueRequest` - New model for async requests with expiration
- `ClueResponse` - New model for hider responses

## Frontend Integration Requirements

### For Seekers
1. Enhanced clue history display showing pending/completed status
2. Real-time updates for incoming clue responses
3. Map integration for exact location clues
4. Photo display for selfie responses

### For Hiders
1. Mandatory popup system for incoming clue requests
2. Camera integration for selfie capture
3. Dropdown/selection for landmark types
4. Real-time request notifications
5. Timeout indicators and automatic popup closure

### Location Requirements
- Both seekers and hiders must have location services enabled
- Location accuracy affects clue targeting precision
- Background location tracking continues for hiders

## Maintenance & Cleanup
- Scheduled task runs every minute to mark expired requests
- Automatic cleanup of broken WebSocket connections
- File cleanup (can be implemented later for disk space management)

## Configuration Changes
- Added file upload size limits in `application.properties`
- Enabled scheduling in main application class
- Added static file serving configuration
- Enhanced WebSocket event types

This refactored system provides a more engaging and interactive clue experience while maintaining the core game mechanics and ensuring fair play through location-based targeting and timeout mechanisms.
