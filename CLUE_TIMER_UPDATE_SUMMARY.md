# Clue Timer Update Implementation Summary

## Changes Made

### Backend Changes

#### 1. ClueRequest Timer Reduction
**File:** `hideandseek_backend/src/main/java/com/hideandseek/model/ClueRequest.java`
- **Change:** Updated constructor to set expiration to 1 minute instead of 5 minutes
- **Line 34:** Changed `(5 * 60 * 1000)` to `(1 * 60 * 1000)`
- **Impact:** All new selfie and closest-landmark clue requests now have a 1-minute timeout

#### 2. Enhanced ClueMaintenanceService
**File:** `hideandseek_backend/src/main/java/com/hideandseek/service/ClueMaintenanceService.java`
- **New Dependencies:** Added GameWebSocketHandler and PushService
- **Enhanced:** `processExpiredClueRequest()` method now provides exact location rewards
- **New Method:** `provideExactLocationReward()` creates and delivers location clues
- **Functionality:** 
  - Automatically creates exact location clue when selfie/landmark requests expire
  - Updates original clue status to "expired"
  - Sends push notifications to both teams
  - Broadcasts game updates via WebSocket

#### 3. New Push Notification Method
**File:** `hideandseek_backend/src/main/java/com/hideandseek/service/PushService.java`
- **New Method:** `notifyClueTimeoutReward()`
- **Functionality:**
  - Sends reward notification to seeker team
  - Sends penalty notification to hider team
  - Includes relevant game context in notification data

#### 4. Updated Clue Type Descriptions
**File:** `hideandseek_backend/src/main/resources/clue_types.json`
- **Updated:** Selfie clue description to mention 1-minute timer and automatic location reward
- **Updated:** Closest landmark clue description to include same timeout information
- **Impact:** Users now see clear information about the timeout mechanism in the UI

### Frontend Compatibility

#### Automatic Adaptation
- **HiderClueListener.tsx:** Already uses backend-provided `expirationTimestamp`, so timer display automatically reflects 1-minute countdown
- **SeekerClueListener.tsx:** No changes needed, already handles location clues appropriately
- **Push Notifications:** Existing push notification system handles new notification types
- **CluesTab.tsx:** Updated clue descriptions automatically display via API

## System Flow

### Normal Flow (Response Within 1 Minute)
1. Seeker purchases selfie/landmark clue → tokens deducted
2. Hider receives clue request with 1-minute timer
3. Hider responds within 1 minute
4. Seeker receives response
5. Original clue marked as "completed"

### Timeout Flow (No Response Within 1 Minute)
1. Seeker purchases selfie/landmark clue → tokens deducted
2. Hider receives clue request with 1-minute timer
3. **Timer expires after 1 minute**
4. **ClueMaintenanceService automatically:**
   - Marks original request as "expired"
   - Creates new exact location clue (cost: 0 tokens)
   - Adds location clue to seeker's history
   - Sends push notifications to both teams
   - Broadcasts game update via WebSocket
5. **Seeker receives:**
   - Push notification about timeout reward
   - Exact location of hider team on map
6. **Hider receives:**
   - Push notification about penalty
   - Awareness that location was revealed

## Notification Messages

### Seeker Reward Notification
- **Title:** "Timeout Reward!"
- **Body:** "You received exact location of [HiderTeamName] because they didn't respond to your [ClueTypeName] request in time."

### Hider Penalty Notification
- **Title:** "Clue Timeout Penalty"
- **Body:** "Your exact location was revealed to seekers because you didn't respond to the [ClueTypeName] request in time."

## Technical Details

### Timer Precision
- **Scheduled Task:** Runs every 60 seconds (ClueMaintenanceService)
- **Accuracy:** Up to 1-minute delay possible between actual expiration and processing
- **Frontend Timer:** Real-time countdown for user awareness

### Cost Structure
- **Original Clue:** Normal cost (10 tokens for selfie, 8 for landmark)
- **Timeout Reward:** Free exact location clue (equivalent to 15-token clue)
- **Net Benefit:** Seekers get more value for unresponsive hiders

### Database Impact
- **In-Memory Only:** No database schema changes required
- **Clue History:** Timeout rewards appear as separate entries
- **Request Tracking:** Original requests marked as "expired"

## Testing Considerations

### Manual Testing Steps
1. Create game with seeker and hider teams
2. Purchase selfie or landmark clue as seeker
3. As hider, receive clue request but don't respond
4. Wait 1-2 minutes for maintenance service to run
5. Verify seeker receives exact location clue
6. Verify both teams receive appropriate push notifications

### Edge Cases Handled
- Game not active during timeout
- Teams not found during processing
- Hider team without location data
- Missing push tokens (graceful failure)
- Multiple expired requests processed in batch

## Backward Compatibility
- **Existing Games:** Will use new 1-minute timer for new requests
- **In-Flight Requests:** Old 5-minute requests continue until they expire
- **Frontend:** Automatically adapts to new timer values
- **API Responses:** No breaking changes to existing endpoints

## Performance Impact
- **Minimal:** One additional DB operation per expired clue
- **Scaling:** Maintenance task runs regardless of active games
- **Memory:** Slight increase for reward clue storage
- **Network:** Additional push notifications and WebSocket broadcasts
