package com.ridelink.payment.dto;

import com.ridelink.payment.model.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representation of estimated trip fare and calculation breakdown.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareEstimateResponse {

    private VehicleType vehicleType;
    private Double distanceKm;
    private Double baseFare;
    private Double ratePerKm;
    private Double distanceFare;
    private Double surgeMultiplier;
    private Double serviceFee;
    private Double estimatedFare;
    @Builder.Default
    private String currency = "LKR";
    private String calculationRule;

    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();
}
