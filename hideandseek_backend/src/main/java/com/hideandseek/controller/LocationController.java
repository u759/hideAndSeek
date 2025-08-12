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
    public ResponseEntity<Void> updateLocation(@RequestBody Map<String, Object> request) {
        System.out.println("POOPOO");
        try {
            String teamId = (String) request.get("teamId");
            String gameId = (String) request.get("gameId");
            Double latitude = ((Number) request.get("latitude")).doubleValue();
            Double longitude = ((Number) request.get("longitude")).doubleValue();
            
            locationService.updateLocation(gameId, teamId, latitude, longitude);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
