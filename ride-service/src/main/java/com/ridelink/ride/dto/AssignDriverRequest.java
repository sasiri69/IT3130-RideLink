package com.ridelink.ride.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for driver assignment.
 * When driverId is omitted, automatic nearest eligible driver selection is performed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDriverRequest {

    private String driverId;
}
