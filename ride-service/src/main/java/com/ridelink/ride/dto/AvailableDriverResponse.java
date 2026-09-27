package com.ridelink.ride.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representation of an eligible available driver returned by Driver Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableDriverResponse {

    private String driverId;
    private String serviceArea;
    private String status;
    private Double rating;
    private Integer totalRides;
    private Double distanceKm;
}
