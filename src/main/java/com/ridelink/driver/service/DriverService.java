package com.ridelink.driver.service;

import com.ridelink.driver.client.AccountServiceClient;
import com.ridelink.driver.client.dto.AccountUserResponse;
import com.ridelink.driver.dto.*;
import com.ridelink.driver.exception.*;
import com.ridelink.driver.model.Driver;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.Vehicle;
import com.ridelink.driver.model.VehicleType;
import com.ridelink.driver.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Driver Service handling operational profiles, vehicle management,
 * availability status transitions, location updates, and dispatch searching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final DriverRepository driverRepository;
    private final AccountServiceClient accountServiceClient;

    /**
     * Registers a new driver operational profile.
     * Synchronously queries the Account Service to verify account existence, DRIVER role, and ACTIVE status.
     *
     * @param request Driver registration request
     * @param authToken Bearer token for interservice authentication
     * @return DriverResponse containing registered profile details
     */
    public DriverResponse registerDriver(DriverRegisterRequest request, String authToken) {
        String driverId = request.getDriverId().trim();
        log.info("Attempting driver operational registration for driverId: {}", driverId);

        // 1. Check local data boundary: has this driver already registered operational details?
        if (driverRepository.existsByDriverId(driverId)) {
            throw new DriverAlreadyRegisteredException(
                    "Driver with account ID '" + driverId + "' has already registered an operational profile."
            );
        }

        // 2. Check unique business constraints (License and Vehicle Plate)
        String licenseNumber = request.getLicenseNumber().trim().toUpperCase();
        if (driverRepository.existsByLicenseNumber(licenseNumber)) {
            throw new DuplicateResourceException(
                    "Driver license number '" + licenseNumber + "' is already registered with another account."
            );
        }

        String licensePlate = request.getVehicle().getLicensePlate().trim().toUpperCase();
        if (driverRepository.existsByVehicleLicensePlate(licensePlate)) {
            throw new DuplicateResourceException(
                    "Vehicle with license plate '" + licensePlate + "' is already registered."
            );
        }

        // 3. Interservice Call to Account Service to verify driver user account
        AccountUserResponse accountUser = accountServiceClient.getUserById(driverId, authToken);

        if (accountUser == null) {
            throw new DriverAccountNotFoundException("User account ID '" + driverId + "' was not found in Account Service.");
        }

        // Validate Role (only DRIVER role can register operational details)
        if (!"DRIVER".equalsIgnoreCase(accountUser.getRole())) {
            throw new InvalidDriverAccountException(
                    "User account '" + driverId + "' has role '" + accountUser.getRole()
                            + "'. Only users registered with role 'DRIVER' can create a driver operational profile."
            );
        }

        // Validate Status (only ACTIVE accounts can register operational profiles)
        if (!"ACTIVE".equalsIgnoreCase(accountUser.getStatus())) {
            throw new InvalidDriverAccountException(
                    "User account '" + driverId + "' is currently '" + accountUser.getStatus()
                            + "'. Only ACTIVE accounts may register operational profiles."
            );
        }

        // 4. Build and persist driver operational document in isolated database
        Driver driver = Driver.builder()
                .driverId(driverId)
                .licenseNumber(licenseNumber)
                .serviceArea(request.getServiceArea().trim())
                .status(DriverStatus.OFFLINE) // Initially OFFLINE until driver explicitly declares availability
                .vehicle(request.getVehicle().toEntity())
                .currentLocation(request.getInitialLocation() != null ? request.getInitialLocation().toEntity() : null)
                .rating(5.0)
                .totalRides(0)
                .build();

        Driver saved = driverRepository.save(driver);
        log.info("Driver profile registered successfully. MongoDB ID: {}, driverId: {}", saved.getId(), saved.getDriverId());
        return DriverResponse.fromEntity(saved);
    }

    /**
     * Retrieves driver operational profile by driver ID or document ID.
     *
     * @param driverId Target driver ID
     * @return DriverResponse containing profile details
     */
    public DriverResponse getDriverById(String driverId) {
        Driver driver = findDriverOrThrow(driverId);
        return DriverResponse.fromEntity(driver);
    }

    /**
     * Replaces vehicle details for an existing driver.
     *
     * @param driverId Target driver ID
     * @param request Vehicle payload details
     * @return Updated driver profile response
     */
    public DriverResponse updateVehicle(String driverId, VehicleRequest request) {
        Driver driver = findDriverOrThrow(driverId);

        String newPlate = request.getLicensePlate().trim().toUpperCase();
        if (!newPlate.equalsIgnoreCase(driver.getVehicle().getLicensePlate())
                && driverRepository.existsByVehicleLicensePlate(newPlate)) {
            throw new DuplicateResourceException("Vehicle license plate '" + newPlate + "' is already registered to another driver.");
        }

        driver.setVehicle(request.toEntity());
        Driver updated = driverRepository.save(driver);
        log.info("Vehicle updated for driver: {}", driverId);
        return DriverResponse.fromEntity(updated);
    }

    /**
     * Registers a vehicle for an existing driver who does not currently have one.
     *
     * @param driverId Target driver ID
     * @param request Vehicle payload details
     * @return Updated driver profile response
     */
    public DriverResponse addVehicle(String driverId, VehicleRequest request) {
        Driver driver = findDriverOrThrow(driverId);

        if (driver.getVehicle() != null) {
            throw new DuplicateResourceException(
                    "Driver '" + driverId + "' already has a registered vehicle. Use PUT /api/drivers/" + driverId + "/vehicle to update it."
            );
        }

        String plate = request.getLicensePlate().trim().toUpperCase();
        if (driverRepository.existsByVehicleLicensePlate(plate)) {
            throw new DuplicateResourceException("Vehicle license plate '" + plate + "' is already registered to another driver.");
        }

        driver.setVehicle(request.toEntity());
        Driver updated = driverRepository.save(driver);
        log.info("Vehicle registered for driver: {}", driverId);
        return DriverResponse.fromEntity(updated);
    }

    /**
     * Retrieves the vehicle details for a specific driver.
     *
     * @param driverId Target driver ID
     * @return Vehicle entity
     */
    public Vehicle getVehicle(String driverId) {
        Driver driver = findDriverOrThrow(driverId);
        if (driver.getVehicle() == null) {
            throw new VehicleNotFoundException("Driver '" + driverId + "' does not currently have a registered vehicle.");
        }
        return driver.getVehicle();
    }

    /**
     * Removes/deregisters the vehicle for an existing driver.
     * Prevents deletion while driver is on an active ride (BUSY status).
     * Automatically resets status to OFFLINE if the driver was AVAILABLE.
     *
     * @param driverId Target driver ID
     */
    public void deleteVehicle(String driverId) {
        Driver driver = findDriverOrThrow(driverId);

        if (driver.getVehicle() == null) {
            throw new VehicleNotFoundException("Driver '" + driverId + "' does not currently have a registered vehicle to delete.");
        }

        if (driver.getStatus() == DriverStatus.BUSY) {
            throw new InvalidStatusTransitionException(
                    "Cannot delete vehicle while driver is currently on an active ride (BUSY status)."
            );
        }

        driver.setVehicle(null);
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
            log.info("Driver {} vehicle removed while AVAILABLE; switching status to OFFLINE", driverId);
            driver.setStatus(DriverStatus.OFFLINE);
        }

        driverRepository.save(driver);
        log.info("Vehicle successfully deleted for driver: {}", driverId);
    }

    /**
     * Updates driver availability status.
     * Enforces valid business state transitions.
     *
     * @param driverId Target driver ID
     * @param request Target status payload
     * @return Updated driver profile response
     */
    public DriverResponse updateAvailability(String driverId, AvailabilityUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);
        DriverStatus currentStatus = driver.getStatus();
        DriverStatus targetStatus = request.getStatus();

        // Enforce business state transition rules
        if (currentStatus == DriverStatus.SUSPENDED && targetStatus == DriverStatus.AVAILABLE) {
            throw new InvalidStatusTransitionException(
                    "Driver account is SUSPENDED and cannot declare availability. Contact support."
            );
        }

        if (currentStatus == DriverStatus.BUSY && targetStatus == DriverStatus.AVAILABLE) {
            log.info("Driver {} transitioning from BUSY to AVAILABLE after completing ride", driverId);
        }

        driver.setStatus(targetStatus);
        Driver updated = driverRepository.save(driver);
        log.info("Availability status for driver {} updated from {} to {}", driverId, currentStatus, targetStatus);
        return DriverResponse.fromEntity(updated);
    }

    /**
     * Updates simulated GPS location coordinates.
     *
     * @param driverId Target driver ID
     * @param request Location update request
     * @return Updated driver profile response
     */
    public DriverResponse updateLocation(String driverId, LocationUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);
        driver.setCurrentLocation(request.toEntity());
        Driver updated = driverRepository.save(driver);
        log.info("Location updated for driver {}: ({}, {})", driverId, request.getLatitude(), request.getLongitude());
        return DriverResponse.fromEntity(updated);
    }

    /**
     * Updates operational service area region.
     *
     * @param driverId Target driver ID
     * @param request Service area update payload
     * @return Updated driver profile response
     */
    public DriverResponse updateServiceArea(String driverId, ServiceAreaUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);
        driver.setServiceArea(request.getServiceArea().trim());
        Driver updated = driverRepository.save(driver);
        log.info("Service area updated for driver {}: {}", driverId, request.getServiceArea());
        return DriverResponse.fromEntity(updated);
    }

    /**
     * Retrieves eligible available drivers matching optional service area and vehicle type filters.
     * Used by the Ride Management Service during ride assignment.
     *
     * @param serviceArea Optional service area filter
     * @param vehicleType Optional vehicle type filter
     * @return List of eligible available drivers
     */
    public List<AvailableDriverResponse> getEligibleAvailableDrivers(String serviceArea, VehicleType vehicleType) {
        List<Driver> drivers;

        if (serviceArea != null && !serviceArea.isBlank() && vehicleType != null) {
            drivers = driverRepository.findByStatusAndServiceAreaIgnoreCaseAndVehicleVehicleType(
                    DriverStatus.AVAILABLE,
                    serviceArea.trim(),
                    vehicleType
            );
        } else if (serviceArea != null && !serviceArea.isBlank()) {
            drivers = driverRepository.findByStatusAndServiceAreaIgnoreCase(
                    DriverStatus.AVAILABLE,
                    serviceArea.trim()
            );
        } else if (vehicleType != null) {
            drivers = driverRepository.findByStatus(DriverStatus.AVAILABLE).stream()
                    .filter(d -> d.getVehicle() != null && d.getVehicle().getVehicleType() == vehicleType)
                    .toList();
        } else {
            drivers = driverRepository.findByStatus(DriverStatus.AVAILABLE);
        }

        return drivers.stream()
                .map(d -> AvailableDriverResponse.fromEntity(d, null))
                .toList();
    }

    /**
     * Finds eligible available drivers within a specified distance using the Haversine formula.
     *
     * @param clientLat Target latitude
     * @param clientLon Target longitude
     * @param radiusKm Maximum radius in kilometers
     * @param vehicleType Optional vehicle type filter
     * @return List of nearby drivers sorted by distance
     */
    public List<AvailableDriverResponse> getNearbyAvailableDrivers(
            Double clientLat,
            Double clientLon,
            Double radiusKm,
            VehicleType vehicleType
    ) {
        double maxRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 10.0; // default 10 km

        List<Driver> availableDrivers = driverRepository.findByStatus(DriverStatus.AVAILABLE);

        return availableDrivers.stream()
                .filter(d -> d.getCurrentLocation() != null
                        && d.getCurrentLocation().getLatitude() != null
                        && d.getCurrentLocation().getLongitude() != null)
                .filter(d -> vehicleType == null || (d.getVehicle() != null && d.getVehicle().getVehicleType() == vehicleType))
                .map(d -> {
                    double distance = calculateHaversineDistanceKm(
                            clientLat, clientLon,
                            d.getCurrentLocation().getLatitude(),
                            d.getCurrentLocation().getLongitude()
                    );
                    double roundedDistance = Math.round(distance * 100.0) / 100.0;
                    return AvailableDriverResponse.fromEntity(d, roundedDistance);
                })
                .filter(res -> res.getDistanceKm() <= maxRadius)
                .sorted(Comparator.comparing(AvailableDriverResponse::getDistanceKm))
                .toList();
    }

    /**
     * Retrieves all drivers with optional filtering for administrators.
     *
     * @param status Optional driver status filter
     * @param serviceArea Optional service area filter
     * @param vehicleType Optional vehicle type filter
     * @return List of matching driver profiles
     */
    public List<DriverResponse> getAllDrivers(DriverStatus status, String serviceArea, VehicleType vehicleType) {
        return driverRepository.findAll().stream()
                .filter(d -> status == null || d.getStatus() == status)
                .filter(d -> serviceArea == null || serviceArea.isBlank() || d.getServiceArea().equalsIgnoreCase(serviceArea.trim()))
                .filter(d -> vehicleType == null || (d.getVehicle() != null && d.getVehicle().getVehicleType() == vehicleType))
                .map(DriverResponse::fromEntity)
                .toList();
    }

    /**
     * Administrative status override for driver accounts.
     *
     * @param driverId Target driver ID
     * @param request Status update payload
     * @return Updated driver profile response
     */
    public DriverResponse updateAdminStatus(String driverId, DriverAdminStatusUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);
        driver.setStatus(request.getStatus());
        Driver updated = driverRepository.save(driver);
        log.info("Admin updated status for driver {} to {}", driverId, request.getStatus());
        return DriverResponse.fromEntity(updated);
    }

    private Driver findDriverOrThrow(String driverId) {
        return driverRepository.findByDriverId(driverId.trim())
                .or(() -> driverRepository.findById(driverId.trim()))
                .orElseThrow(() -> new DriverNotFoundException(
                        "Driver profile for ID '" + driverId + "' was not found."
                ));
    }

    /**
     * Computes great-circle distance between two GPS coordinates using the Haversine formula.
     */
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
