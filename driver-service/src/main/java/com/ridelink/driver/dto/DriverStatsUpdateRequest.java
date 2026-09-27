package com.ridelink.driver.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a driver's ride statistics after a completed ride.
 * <p>
 * Used by the Ride Management Service via synchronous inter-service REST call
 * (Lecture 08 – Communication Interfaces II) to keep driver stats consistent
 * across service boundaries without cross-database access (Assignment §6.1).
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatsUpdateRequest {

    /**
     * New rating received for this ride (1.0 – 5.0).
     * If null, only totalRides is incremented; the running average is unchanged.
     */
    @DecimalMin(value = "1.0", message = "Rating must be between 1.0 and 5.0")
    @DecimalMax(value = "5.0", message = "Rating must be between 1.0 and 5.0")
    private Double newRating;

    /**
     * Number of completed rides to add to the running total.
     * Typically 1 per completed ride.
     */
    @Min(value = 0, message = "Rides increment must be a non-negative number")
    private int ridesIncrement;
}
