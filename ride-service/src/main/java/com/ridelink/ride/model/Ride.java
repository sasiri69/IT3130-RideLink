package com.ridelink.ride.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Ride document representing a ride booking and its operational lifecycle.
 */
@Document(collection = "rides")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    private String id;

    @Indexed(unique = true)
    private String rideId;

    @Indexed
    private String passengerId;

    @Indexed
    private String driverId;

    private LocationPoint pickupLocation;
    private LocationPoint destinationLocation;

    private String serviceArea;
    private VehicleType vehicleType;

    @Builder.Default
    private RideStatus status = RideStatus.REQUESTED;

    private Double distanceKm;
    private Double estimatedFare;
    private Double finalFare;

    private String cancellationReason;
    private String cancelledBy;

    private LocalDateTime requestedAt;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
