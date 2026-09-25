package com.ridelink.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ride representation received from Ride Management Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideClientResponse {
    private String id;
    private String rideId;
    private String passengerId;
    private String driverId;
    private String status;
    private Double distanceKm;
    private Double estimatedFare;
    private Double finalFare;
}
