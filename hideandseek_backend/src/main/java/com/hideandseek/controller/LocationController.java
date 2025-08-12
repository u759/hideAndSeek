package com.hideandseek.controller;

import com.hideandseek.model.Location;
import com.hideandseek.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/location")
@CrossOrigin(origins = "*")
public class LocationController {

    @Autowired
    private LocationService locationService;

    @PostMapping("/update")
    public ResponseEntity<String> updateLocation(@RequestBody Map<String, Object> request) {
        try {
            String teamId = (String) request.get("teamId");
            String gameId = (String) request.get("gameId");
            Double latitude = ((Number) request.get("latitude")).doubleValue();
            Double longitude = ((Number) request.get("longitude")).doubleValue();
            
            locationService.updateLocation(gameId, teamId, latitude, longitude);
            return ResponseEntity.ok("Location updated successfully");
        } catch (IllegalStateException e) {
            // Game status validation error
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException e) {
            // Game/team not found error
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // Other errors
            return ResponseEntity.badRequest().body("Failed to update location: " + e.getMessage());
        }
    }
}
