package com.ridelink.ride.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Geographical coordinate point with descriptive location name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPoint {

    private Double latitude;
    private Double longitude;
    private String addressName;
}
