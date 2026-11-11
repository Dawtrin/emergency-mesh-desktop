package com.rescue.mesh.controller;

import com.rescue.mesh.service.InterAgencyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API cơ sở hạ tầng dã chiến (Bệnh viện, PCCC, Công an) cho bản đồ GIS.
 *
 * Endpoints (theo FINAL spec §6.1):
 *   GET /api/facilities?type= — Danh sách cơ sở, lọc tùy chọn theo type
 */
@RestController
@RequestMapping("/api/facilities")
public class FacilityApiController {

    private final InterAgencyService interAgencyService;

    public FacilityApiController(InterAgencyService interAgencyService) {
        this.interAgencyService = interAgencyService;
    }

    /**
     * Danh sách cơ sở cấp cứu & an ninh dã chiến (GIS layer).
     * Filter tùy chọn: HOSPITAL, FIRE_STATION, POLICE_POST
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getFacilities(
            @RequestParam(required = false) String type) {
        List<Map<String, Object>> all = interAgencyService.getFacilities();
        if (type != null && !type.isBlank()) {
            return all.stream()
                    .filter(f -> type.equalsIgnoreCase((String) f.get("facilityType")))
                    .toList();
        }
        return all;
    }

    /**
     * Cập nhật sức chứa cơ sở dã chiến
     */
    @PostMapping(value = "/{facilityId}/capacity",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateCapacity(
            @PathVariable String facilityId,
            @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        boolean ok = interAgencyService.updateFacilityCapacity(facilityId, delta);
        return Map.of("status", ok ? "UPDATED" : "FAILED", "facilityId", facilityId, "delta", delta);
    }
}
