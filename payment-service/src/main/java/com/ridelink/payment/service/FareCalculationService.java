package com.ridelink.payment.service;

import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.FareEstimateResponse;
import com.ridelink.payment.exception.InvalidPaymentRequestException;
import com.ridelink.payment.model.VehicleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business service responsible for trip fare estimation and calculation logic.
 */
@Service
@Slf4j
public class FareCalculationService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calculates estimated fare according to documented rate cards and distance.
     *
     * @param request Estimation request payload
     * @return FareEstimateResponse breakdown
     */
    public FareEstimateResponse calculateFareEstimate(FareEstimateRequest request) {
        if (request == null) {
            throw new InvalidPaymentRequestException("Estimation request payload cannot be null.");
        }

        double distanceKm;

        if (request.getDirectDistanceKm() != null) {
            if (request.getDirectDistanceKm() <= 0) {
                throw new InvalidPaymentRequestException("Direct distance must be greater than zero.");
            }
            distanceKm = request.getDirectDistanceKm();
        } else if (request.getPickupLatitude() != null && request.getPickupLongitude() != null
                && request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
            
            distanceKm = calculateHaversineDistanceKm(
                    request.getPickupLatitude(), request.getPickupLongitude(),
                    request.getDestinationLatitude(), request.getDestinationLongitude()
            );

            if (distanceKm < 0.1) {
                throw new InvalidPaymentRequestException("Pickup and destination coordinates are too close or identical (minimum distance is 0.1 km).");
            }
        } else {
            throw new InvalidPaymentRequestException("Either directDistanceKm or complete pickup and destination coordinates must be provided.");
        }

        distanceKm = Math.round(distanceKm * 100.0) / 100.0;
        VehicleType vehicleType = request.getVehicleType();

        double baseFare = getBaseFare(vehicleType);
        double ratePerKm = getRatePerKm(vehicleType);
        double distanceFare = Math.round((distanceKm * ratePerKm) * 100.0) / 100.0;
        double subtotal = baseFare + distanceFare;

        double surgeMultiplier = request.getSurgeMultiplier() != null ? request.getSurgeMultiplier() : 1.0;
        if (surgeMultiplier < 1.0 || surgeMultiplier > 3.0) {
            throw new InvalidPaymentRequestException("Surge multiplier must be between 1.0 and 3.0.");
        }

        double serviceFee = Math.round((subtotal * 0.05) * 100.0) / 100.0;
        double estimatedTotal = Math.round(((subtotal * surgeMultiplier) + serviceFee) * 100.0) / 100.0;

        String ruleExplanation = String.format(
                "Fare Rule: Base Fare (LKR %.2f) + Distance (%.2f km * LKR %.2f/km = LKR %.2f) * Surge (%.1fx) + Service Fee (5%% = LKR %.2f)",
                baseFare, distanceKm, ratePerKm, distanceFare, surgeMultiplier, serviceFee
        );

        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/fares/estimate");
        links.put("payment", "/api/payments");

        return FareEstimateResponse.builder()
                .vehicleType(vehicleType)
                .distanceKm(distanceKm)
                .baseFare(baseFare)
                .ratePerKm(ratePerKm)
                .distanceFare(distanceFare)
                .surgeMultiplier(surgeMultiplier)
                .serviceFee(serviceFee)
                .estimatedFare(estimatedTotal)
                .currency("LKR")
                .calculationRule(ruleExplanation)
                .links(links)
                .build();
    }

    public double getBaseFare(VehicleType vehicleType) {
        if (vehicleType == null) return 350.0;
        return switch (vehicleType) {
            case CAR -> 350.0;
            case VAN -> 500.0;
            case BIKE -> 150.0;
            case TUKTUK -> 200.0;
        };
    }

    public double getRatePerKm(VehicleType vehicleType) {
        if (vehicleType == null) return 100.0;
        return switch (vehicleType) {
            case CAR -> 100.0;
            case VAN -> 140.0;
            case BIKE -> 50.0;
            case TUKTUK -> 70.0;
        };
    }

    private double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
