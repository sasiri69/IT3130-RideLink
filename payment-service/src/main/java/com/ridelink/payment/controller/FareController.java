package com.ridelink.payment.controller;

import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.FareEstimateResponse;
import com.ridelink.payment.service.FareCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for trip fare estimation and pricing calculations.
 */
@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fare Estimation", description = "Endpoints for trip fare estimates and pricing rules")
public class FareController {

    private final FareCalculationService fareCalculationService;

    @PostMapping("/estimate")
    @Operation(summary = "Calculate trip fare estimate", description = "Calculates estimated fare using base fare, distance rate, and surge factor.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fare estimated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid estimation coordinates or distance")
    })
    public ResponseEntity<FareEstimateResponse> getFareEstimate(@Valid @RequestBody FareEstimateRequest request) {
        log.info("REST POST /api/fares/estimate for vehicle type: {}", request.getVehicleType());
        FareEstimateResponse response = fareCalculationService.calculateFareEstimate(request);
        return ResponseEntity.ok(response);
    }
}
