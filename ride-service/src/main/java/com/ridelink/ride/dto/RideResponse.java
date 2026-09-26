package com.ridelink.ride.dto;

import com.ridelink.ride.model.LocationPoint;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.model.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public response DTO for ride details with navigational hypermedia links.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideResponse {

    private String id;
    private String rideId;
    private String passengerId;
    private String driverId;
    private LocationPoint pickupLocation;
    private LocationPoint destinationLocation;
    private String serviceArea;
    private VehicleType vehicleType;
    private RideStatus status;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();

    public static RideResponse fromEntity(Ride ride) {
        if (ride == null) {
            return null;
        }

        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/rides/" + ride.getRideId());

        if (ride.getStatus() == RideStatus.REQUESTED) {
            links.put("assignDriver", "/api/rides/" + ride.getRideId() + "/assign-driver");
            links.put("cancel", "/api/rides/" + ride.getRideId() + "/cancel");
        } else if (ride.getStatus() == RideStatus.ASSIGNED) {
            links.put("accept", "/api/rides/" + ride.getRideId() + "/accept");
            links.put("cancel", "/api/rides/" + ride.getRideId() + "/cancel");
        } else if (ride.getStatus() == RideStatus.ACCEPTED) {
            links.put("start", "/api/rides/" + ride.getRideId() + "/start");
            links.put("cancel", "/api/rides/" + ride.getRideId() + "/cancel");
        } else if (ride.getStatus() == RideStatus.IN_PROGRESS) {
            links.put("complete", "/api/rides/" + ride.getRideId() + "/complete");
        }

        return RideResponse.builder()
                .id(ride.getId())
                .rideId(ride.getRideId())
                .passengerId(ride.getPassengerId())
                .driverId(ride.getDriverId())
                .pickupLocation(ride.getPickupLocation())
                .destinationLocation(ride.getDestinationLocation())
                .serviceArea(ride.getServiceArea())
                .vehicleType(ride.getVehicleType())
                .status(ride.getStatus())
                .distanceKm(ride.getDistanceKm())
                .estimatedFare(ride.getEstimatedFare())
                .finalFare(ride.getFinalFare())
                .cancellationReason(ride.getCancellationReason())
                .cancelledBy(ride.getCancelledBy())
                .requestedAt(ride.getRequestedAt())
                .assignedAt(ride.getAssignedAt())
                .acceptedAt(ride.getAcceptedAt())
                .startedAt(ride.getStartedAt())
                .completedAt(ride.getCompletedAt())
                .cancelledAt(ride.getCancelledAt())
                .createdAt(ride.getCreatedAt())
                .updatedAt(ride.getUpdatedAt())
                .links(links)
                .build();
    }
}
