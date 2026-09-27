package com.ridelink.ride.service;

import com.ridelink.ride.client.AccountServiceClient;
import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.dto.AccountUserResponse;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.exception.*;
import com.ridelink.ride.model.LocationPoint;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.model.VehicleType;
import com.ridelink.ride.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ride Management Service handling ride request creation, driver assignment,
 * and the complete lifecycle state machine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final RideRepository rideRepository;
    private final DriverServiceClient driverServiceClient;
    private final AccountServiceClient accountServiceClient;

    /**
     * Creates a new ride booking request in REQUESTED state.
     * Verifies passenger account with the Account Service.
     *
     * @param request Booking details
     * @param authToken Optional user auth token
     * @return Created ride response
     */
    public RideResponse createRide(RideRequest request, String authToken) {
        String passengerId = request.getPassengerId().trim();
        log.info("Attempting to create ride request for passenger: {}", passengerId);

        // 1. Verify passenger exists and has active account with Account Service
        AccountUserResponse passenger = accountServiceClient.getPassengerById(passengerId, authToken);
        if (passenger != null && !"ACTIVE".equalsIgnoreCase(passenger.getStatus())) {
            throw new InvalidPassengerAccountException("Passenger account '" + passengerId + "' is not active.");
        }

        // 2. Calculate trip distance
        LocationPoint pickup = request.getPickupLocation().toEntity();
        LocationPoint destination = request.getDestinationLocation().toEntity();
        double distanceKm = calculateHaversineDistanceKm(
                pickup.getLatitude(), pickup.getLongitude(),
                destination.getLatitude(), destination.getLongitude()
        );
        distanceKm = Math.max(1.0, Math.round(distanceKm * 100.0) / 100.0);

        // 3. Compute estimated fare based on vehicle category
        double estimatedFare = calculateEstimatedFare(distanceKm, request.getVehicleType());

        // 4. Generate unique public ride ID
        String publicRideId = "RIDE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Ride ride = Ride.builder()
                .rideId(publicRideId)
                .passengerId(passengerId)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .serviceArea(request.getServiceArea().trim())
                .vehicleType(request.getVehicleType())
                .status(RideStatus.REQUESTED)
                .distanceKm(distanceKm)
                .estimatedFare(estimatedFare)
                .requestedAt(LocalDateTime.now())
                .build();

        Ride saved = rideRepository.save(ride);
        log.info("Ride created successfully with ID: {}", saved.getRideId());
        return RideResponse.fromEntity(saved);
    }

    /**
     * Assigns an eligible driver to a ride booking.
     * Supports manual driver assignment or automatic selection of the nearest available driver.
     *
     * @param rideId Target ride ID
     * @param request Optional driver assignment payload
     * @param authToken Optional auth token
     * @return Updated ride response in ASSIGNED state
     */
    public RideResponse assignDriver(String rideId, AssignDriverRequest request, String authToken) {
        Ride ride = findRideOrThrow(rideId);

        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new InvalidStateTransitionException(
                    "Cannot assign driver to ride in status: " + ride.getStatus() + ". Ride must be in REQUESTED state."
            );
        }

        String assignedDriverId;

        if (request != null && request.getDriverId() != null && !request.getDriverId().isBlank()) {
            assignedDriverId = request.getDriverId().trim();
        } else {
            // Auto-select nearest/first eligible available driver from Driver Service
            List<AvailableDriverResponse> availableDrivers = driverServiceClient.getAvailableDrivers(
                    ride.getServiceArea(),
                    ride.getVehicleType()
            );

            if (availableDrivers.isEmpty()) {
                throw new NoAvailableDriverException(
                        "No available drivers currently found in service area '" + ride.getServiceArea()
                                + "' for vehicle type: " + ride.getVehicleType()
                );
            }

            assignedDriverId = availableDrivers.get(0).getDriverId();
        }

        ride.setDriverId(assignedDriverId);
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setAssignedAt(LocalDateTime.now());

        Ride updated = rideRepository.save(ride);
        log.info("Driver {} assigned to ride {}", assignedDriverId, rideId);
        return RideResponse.fromEntity(updated);
    }

    /**
     * Driver confirms and accepts the assigned ride booking.
     * Updates driver availability status to BUSY via Driver Service.
     *
     * @param rideId Target ride ID
     * @param driverId Confirming driver ID
     * @param authToken Optional auth token
     * @return Updated ride response in ACCEPTED state
     */
    public RideResponse acceptRide(String rideId, String driverId, String authToken) {
        Ride ride = findRideOrThrow(rideId);

        if (ride.getStatus() != RideStatus.ASSIGNED) {
            throw new InvalidStateTransitionException(
                    "Cannot accept ride in status: " + ride.getStatus() + ". Ride must be in ASSIGNED state."
            );
        }

        if (driverId != null && !driverId.isBlank() && !driverId.trim().equalsIgnoreCase(ride.getDriverId())) {
            throw new UnauthorizedRideAccessException("Driver " + driverId + " is not the assigned driver for this ride.");
        }

        ride.setStatus(RideStatus.ACCEPTED);
        ride.setAcceptedAt(LocalDateTime.now());

        // Interservice update: Driver is now engaged on a trip
        driverServiceClient.updateDriverAvailability(ride.getDriverId(), "BUSY", authToken);

        Ride updated = rideRepository.save(ride);
        log.info("Ride {} accepted by driver {}", rideId, ride.getDriverId());
        return RideResponse.fromEntity(updated);
    }

    /**
     * Driver starts the trip once passenger is on board.
     *
     * @param rideId Target ride ID
     * @param driverId Confirming driver ID
     * @param authToken Optional auth token
     * @return Updated ride response in IN_PROGRESS state
     */
    public RideResponse startRide(String rideId, String driverId, String authToken) {
        Ride ride = findRideOrThrow(rideId);

        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw new InvalidStateTransitionException(
                    "Cannot start ride in status: " + ride.getStatus() + ". Ride must be in ACCEPTED state."
            );
        }

        if (driverId != null && !driverId.isBlank() && !driverId.trim().equalsIgnoreCase(ride.getDriverId())) {
            throw new UnauthorizedRideAccessException("Driver " + driverId + " is not the assigned driver for this ride.");
        }

        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(LocalDateTime.now());

        Ride updated = rideRepository.save(ride);
        log.info("Ride {} started by driver {}", rideId, ride.getDriverId());
        return RideResponse.fromEntity(updated);
    }

    /**
     * Driver completes the trip at destination.
     * Calculates final fare and restores driver availability to AVAILABLE via Driver Service.
     *
     * @param rideId Target ride ID
     * @param driverId Confirming driver ID
     * @param authToken Optional auth token
     * @return Updated ride response in COMPLETED state
     */
    public RideResponse completeRide(String rideId, String driverId, String authToken) {
        Ride ride = findRideOrThrow(rideId);

        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "Cannot complete ride in status: " + ride.getStatus() + ". Ride must be in IN_PROGRESS state."
            );
        }

        if (driverId != null && !driverId.isBlank() && !driverId.trim().equalsIgnoreCase(ride.getDriverId())) {
            throw new UnauthorizedRideAccessException("Driver " + driverId + " is not the assigned driver for this ride.");
        }

        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        ride.setFinalFare(ride.getEstimatedFare());

        // Interservice update 1: Driver is now available for new rides
        driverServiceClient.updateDriverAvailability(ride.getDriverId(), "AVAILABLE", authToken);

        // Interservice update 2: Increment driver's totalRides and update rolling average rating
        // (Assignment §6.2 – inter-service communication; default rating 4.0 when no passenger rating provided)
        driverServiceClient.updateDriverStats(ride.getDriverId(), 4.0);

        Ride updated = rideRepository.save(ride);
        log.info("Ride {} completed. Final fare: LKR {}", rideId, ride.getFinalFare());
        return RideResponse.fromEntity(updated);
    }

    /**
     * Cancels an ongoing booking before the ride has started.
     * Releases assigned driver if applicable.
     *
     * @param rideId Target ride ID
     * @param request Cancellation payload containing reason
     * @param cancelledById User performing cancellation
     * @param authToken Optional auth token
     * @return Updated ride response in CANCELLED state
     */
    public RideResponse cancelRide(String rideId, CancelRideRequest request, String cancelledById, String authToken) {
        Ride ride = findRideOrThrow(rideId);

        if (ride.getStatus() == RideStatus.IN_PROGRESS || ride.getStatus() == RideStatus.COMPLETED) {
            throw new InvalidStateTransitionException(
                    "Cannot cancel a ride that is already " + ride.getStatus() + "."
            );
        }

        if (ride.getStatus() == RideStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Ride is already cancelled.");
        }

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(LocalDateTime.now());
        ride.setCancellationReason(request.getReason().trim());
        ride.setCancelledBy(cancelledById != null ? cancelledById : "USER");

        // If a driver was already assigned or accepted, release driver back to AVAILABLE
        if (ride.getDriverId() != null) {
            driverServiceClient.updateDriverAvailability(ride.getDriverId(), "AVAILABLE", authToken);
        }

        Ride updated = rideRepository.save(ride);
        log.info("Ride {} cancelled by {}. Reason: {}", rideId, ride.getCancelledBy(), request.getReason());
        return RideResponse.fromEntity(updated);
    }

    /**
     * Retrieves ride by public rideId or database ID.
     */
    public RideResponse getRideById(String rideId) {
        Ride ride = findRideOrThrow(rideId);
        return RideResponse.fromEntity(ride);
    }

    /**
     * Retrieves ride history for a specific passenger.
     */
    public List<RideResponse> getRidesByPassenger(String passengerId) {
        return rideRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId.trim())
                .stream()
                .map(RideResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves assigned rides for a specific driver.
     */
    public List<RideResponse> getRidesByDriver(String driverId) {
        return rideRepository.findByDriverIdOrderByCreatedAtDesc(driverId.trim())
                .stream()
                .map(RideResponse::fromEntity)
                .toList();
    }

    /**
     * Lists rides filtered by operational status.
     */
    public List<RideResponse> getAllRides(RideStatus status) {
        List<Ride> rides = (status != null)
                ? rideRepository.findByStatus(status)
                : rideRepository.findAll();

        return rides.stream()
                .map(RideResponse::fromEntity)
                .toList();
    }

    private Ride findRideOrThrow(String rideId) {
        return rideRepository.findByRideId(rideId.trim())
                .or(() -> rideRepository.findById(rideId.trim()))
                .orElseThrow(() -> new RideNotFoundException("Ride with identifier '" + rideId + "' was not found."));
    }

    private double calculateEstimatedFare(double distanceKm, VehicleType vehicleType) {
        double baseFare;
        double ratePerKm;

        switch (vehicleType) {
            case BIKE -> {
                baseFare = 100.0;
                ratePerKm = 60.0;
            }
            case TUKTUK -> {
                baseFare = 150.0;
                ratePerKm = 85.0;
            }
            case VAN -> {
                baseFare = 350.0;
                ratePerKm = 160.0;
            }
            case CAR -> {
                baseFare = 250.0;
                ratePerKm = 120.0;
            }
            default -> {
                baseFare = 200.0;
                ratePerKm = 100.0;
            }
        }

        double total = baseFare + (distanceKm * ratePerKm);
        return Math.round(total * 100.0) / 100.0;
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
