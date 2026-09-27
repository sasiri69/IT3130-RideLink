package com.ridelink.ride.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload sent to Driver Service when updating driver availability status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAvailabilityUpdateRequest {

    private String status;
}
