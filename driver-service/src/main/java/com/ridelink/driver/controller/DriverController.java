package com.ridelink.driver.controller;

import com.ridelink.driver.dto.*;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.VehicleType;
import com.ridelink.driver.security.JwtService;
import com.ridelink.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Driver & Vehicle REST Controller.
 * Provides RESTful endpoints for driver operational profiles, vehicle management,
 * availability status toggling, location tracking, and driver dispatch queries.
 */
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver & Vehicle Service", description = "Endpoints for driver operational profiles, vehicle registration, availability, simulated GPS location, and dispatch querying")
public class DriverController {

    private final DriverService driverService;
    private final JwtService jwtService;

    // ─── 1. Driver Operational Registration ────────────────────────────────────

    /**
     * Registers a new driver operational profile and vehicle.
     * Validates account existence and role with the Account Service.
     *
     * @param request Driver registration details
     * @param authHeader Bearer authentication token
     * @return Created driver response with HTTP 201 Created status
     */
    @PostMapping
    @Operation(
            summary = "Register driver operational profile and vehicle",
            description = "Creates a driver operational profile. Synchronously verifies user account role and status with the Account Service."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver operational profile created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or user is not an active driver in Account Service"),
            @ApiResponse(responseCode = "404", description = "User account ID not found in Account Service"),
            @ApiResponse(responseCode = "409", description = "Driver, license number, or vehicle plate already registered"),
            @ApiResponse(responseCode = "503", description = "Account Service is currently unavailable")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> registerDriver(
            @Valid @RequestBody DriverRegisterRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        log.info("REST POST /api/drivers - Registering driver profile for accountId: {}", request.getDriverId());
        DriverResponse response = driverService.registerDriver(request, authHeader);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─── 2. Own Profile Retrieval ──────────────────────────────────────────────

    /**
     * Retrieves the operational profile of the currently authenticated driver.
     *
     * @param authHeader Bearer authentication token
     * @return Driver profile with HTTP 200 OK status
     */
    @GetMapping("/me")
    @Operation(summary = "Get currently authenticated driver's operational profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
            @ApiResponse(responseCode = "404", description = "Driver profile not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> getMyProfile(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String userId = jwtService.extractUserId(token);
        return ResponseEntity.ok(driverService.getDriverById(userId));
    }

    // ─── 3. Single Driver Profile Retrieval ────────────────────────────────────

    /**
     * Fetches a driver operational profile by driver ID.
     *
     * @param driverId Unique driver account or document identifier
     * @return Driver profile with HTTP 200 OK status
     */
    @GetMapping("/{driverId}")
    @Operation(summary = "Get driver operational profile by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver profile found"),
            @ApiResponse(responseCode = "404", description = "Driver profile not found")
    })
    public ResponseEntity<DriverResponse> getDriverById(
            @Parameter(description = "Driver account ID or document ID") @PathVariable String driverId
    ) {
        return ResponseEntity.ok(driverService.getDriverById(driverId));
    }

    // ─── 4a. Vehicle Sub-resource Creation ─────────────────────────────────────

    /**
     * Registers a vehicle for a driver.
     *
     * @param driverId Driver ID
     * @param request Vehicle registration details
     * @param authHeader Bearer authentication token
     * @return Updated driver profile with HTTP 201 Created status
     */
    @PostMapping("/{driverId}/vehicle")
    @Operation(summary = "Register/create a vehicle for a driver")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid vehicle input"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot add vehicle to another driver's profile"),
            @ApiResponse(responseCode = "404", description = "Driver not found"),
            @ApiResponse(responseCode = "409", description = "Driver already has a vehicle or duplicate plate")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> addVehicle(
            @PathVariable String driverId,
            @Valid @RequestBody VehicleRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateDriverOwnershipOrAdmin(driverId, authHeader);
        DriverResponse response = driverService.addVehicle(driverId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─── 4b. Vehicle Sub-resource Retrieval ────────────────────────────────────

    /**
     * Retrieves vehicle details for a driver.
     *
     * @param driverId Driver ID
     * @return Vehicle entity with HTTP 200 OK status
     */
    @GetMapping("/{driverId}/vehicle")
    @Operation(summary = "Retrieve vehicle details for a driver")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle details found"),
            @ApiResponse(responseCode = "404", description = "Driver or vehicle not found")
    })
    public ResponseEntity<com.ridelink.driver.model.Vehicle> getVehicle(
            @PathVariable String driverId
    ) {
        return ResponseEntity.ok(driverService.getVehicle(driverId));
    }

    // ─── 4c. Vehicle Replacement ───────────────────────────────────────────────

    /**
     * Updates or replaces vehicle details for a driver.
     *
     * @param driverId Driver ID
     * @param request New vehicle details
     * @param authHeader Bearer authentication token
     * @return Updated driver profile with HTTP 200 OK status
     */
    @PutMapping("/{driverId}/vehicle")
    @Operation(summary = "Replace or update vehicle details for a driver")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid vehicle input"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot modify another driver's vehicle"),
            @ApiResponse(responseCode = "404", description = "Driver not found"),
            @ApiResponse(responseCode = "409", description = "License plate already registered to another vehicle")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> updateVehicle(
            @PathVariable String driverId,
            @Valid @RequestBody VehicleRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateDriverOwnershipOrAdmin(driverId, authHeader);
        return ResponseEntity.ok(driverService.updateVehicle(driverId, request));
    }

    // ─── 4d. Vehicle Removal ───────────────────────────────────────────────────

    /**
     * Removes vehicle details for a driver. Returns HTTP 204 No Content.
     * Automatically sets driver status to OFFLINE if driver was AVAILABLE.
     *
     * @param driverId Driver ID
     * @param authHeader Bearer authentication token
     * @return Empty response with HTTP 204 No Content status
     */
    @DeleteMapping("/{driverId}/vehicle")
    @Operation(
            summary = "Remove or deregister vehicle details for a driver",
            description = "Removes vehicle details. Cannot be deleted while driver is BUSY on an active ride. Automatically switches status to OFFLINE if AVAILABLE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vehicle removed successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot remove vehicle while BUSY on active ride"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot remove another driver's vehicle"),
            @ApiResponse(responseCode = "404", description = "Driver or vehicle not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteVehicle(
            @PathVariable String driverId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateDriverOwnershipOrAdmin(driverId, authHeader);
        driverService.deleteVehicle(driverId);
        return ResponseEntity.noContent().build();
    }

    // ─── 5. Availability Status ────────────────────────────────────────────────

    /**
     * Updates driver availability status (e.g. AVAILABLE, OFFLINE).
     *
     * @param driverId Driver ID
     * @param request Status update request
     * @param authHeader Bearer authentication token
     * @return Updated driver profile with HTTP 200 OK status
     */
    @PatchMapping("/{driverId}/availability")
    @Operation(summary = "Update availability status (AVAILABLE or OFFLINE)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition (e.g. suspended driver)"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot modify another driver's status"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> updateAvailability(
            @PathVariable String driverId,
            @Valid @RequestBody AvailabilityUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateDriverOwnershipOrAdmin(driverId, authHeader);
        return ResponseEntity.ok(driverService.updateAvailability(driverId, request));
    }

    // ─── 6. Simulated GPS Location Update ──────────────────────────────────────

    /**
     * Updates driver's current simulated GPS location.
     *
     * @param driverId Driver ID
     * @param request Location update payload containing latitude and longitude
     * @param authHeader Bearer authentication token
     * @return Updated driver profile with HTTP 200 OK status
     */
    @PatchMapping("/{driverId}/location")
    @Operation(summary = "Update simulated GPS coordinates and address")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid coordinate ranges"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Cannot update another driver's location"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> updateLocation(
            @PathVariable String driverId,
            @Valid @RequestBody LocationUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateDriverOwnershipOrAdmin(driverId, authHeader);
        return ResponseEntity.ok(driverService.updateLocation(driverId, request));
    }

    // ─── 7. Operational Service Area Update ────────────────────────────────────

    /**
     * Updates driver's operating service area region.
     *
     * @param driverId Driver ID
     * @param request Service area update request
     * @param authHeader Bearer authentication token
     * @return Updated driver profile with HTTP 200 OK status
     */
    @PatchMapping("/{driverId}/service-area")
    @Operation(summary = "Update operational service area (e.g. Colombo, Kandy, Galle, Malabe)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service area updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid service area input"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DriverResponse> updateServiceArea(
            @PathVariable String driverId,
            @Valid @RequestBody ServiceAreaUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateDriverOwnershipOrAdmin(driverId, authHeader);
        return ResponseEntity.ok(driverService.updateServiceArea(driverId, request));
    }

    // ─── 8. Eligible Available Drivers ─────────────────────────────────────────

    /**
     * Queries available drivers matching optional service area and vehicle type criteria.
     * Used by Ride Management Service for ride dispatching.
     *
     * @param serviceArea Optional service area filter
     * @param vehicleType Optional vehicle type filter
     * @return List of eligible available drivers
     */
    @GetMapping("/available")
    @Operation(
            summary = "Retrieve eligible available drivers for dispatching",
            description = "Filters available drivers by service area and vehicle type using query parameters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of eligible available drivers returned")
    })
    public ResponseEntity<List<AvailableDriverResponse>> getAvailableDrivers(
            @Parameter(description = "Filter by service area (e.g. Colombo, Malabe)")
            @RequestParam(required = false) String serviceArea,
            @Parameter(description = "Filter by vehicle type (CAR, VAN, BIKE, TUKTUK)")
            @RequestParam(required = false) VehicleType vehicleType
    ) {
        return ResponseEntity.ok(driverService.getEligibleAvailableDrivers(serviceArea, vehicleType));
    }

    // ─── 9. Proximity Search for Simulated Nearby Drivers ───────────────────────

    /**
     * Finds nearby available drivers based on Haversine distance calculation.
     *
     * @param latitude Target latitude
     * @param longitude Target longitude
     * @param radiusKm Search radius in kilometers (default: 10km)
     * @param vehicleType Optional vehicle type filter
     * @return List of nearby available drivers sorted by distance
     */
    @GetMapping("/nearby")
    @Operation(
            summary = "Retrieve available drivers nearby simulated GPS coordinates",
            description = "Calculates Haversine distance from client coordinates and returns available drivers within specified radius."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of nearby drivers returned sorted by distance"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid coordinates")
    })
    public ResponseEntity<List<AvailableDriverResponse>> getNearbyDrivers(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false, defaultValue = "10.0") Double radiusKm,
            @RequestParam(required = false) VehicleType vehicleType
    ) {
        return ResponseEntity.ok(driverService.getNearbyAvailableDrivers(latitude, longitude, radiusKm, vehicleType));
    }

    // ─── 10. Administrator Listing ─────────────────────────────────────────────

    /**
     * Lists all registered drivers with optional filtering by status, service area, or vehicle type.
     * Requires ADMIN role.
     *
     * @param status Optional driver status filter
     * @param serviceArea Optional service area filter
     * @param vehicleType Optional vehicle type filter
     * @return List of matching driver profiles
     */
    @GetMapping
    @Operation(summary = "List all drivers with optional filters (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of drivers returned"),
            @ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required")
    })
    public ResponseEntity<List<DriverResponse>> getAllDrivers(
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) String serviceArea,
            @RequestParam(required = false) VehicleType vehicleType
    ) {
        return ResponseEntity.ok(driverService.getAllDrivers(status, serviceArea, vehicleType));
    }

    // ─── 11. Administrator Status Override ─────────────────────────────────────

    /**
     * Administrative override of driver operational status (e.g., suspending a driver).
     * Requires ADMIN role.
     *
     * @param driverId Target driver ID
     * @param request Status update payload
     * @return Updated driver profile
     */
    @PatchMapping("/{driverId}/admin-status")
    @Operation(summary = "Administrative driver status update (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver status updated by administrator"),
            @ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
    })
    public ResponseEntity<DriverResponse> updateAdminStatus(
            @PathVariable String driverId,
            @Valid @RequestBody DriverAdminStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(driverService.updateAdminStatus(driverId, request));
    }

    // ─── Helper for Role / Ownership verification ───────────────────────────────

    /**
     * Validates that the requesting caller is either the owner of the driver profile or has ADMIN privileges.
     *
     * @param driverId Target driver ID
     * @param authHeader Bearer token header
     */
    private void validateDriverOwnershipOrAdmin(String driverId, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        try {
            String role = jwtService.extractRole(token);
            String tokenUserId = jwtService.extractUserId(token);
            if (!"ADMIN".equalsIgnoreCase(role) && (tokenUserId != null && !tokenUserId.equals(driverId))) {
                throw new AccessDeniedException("Access denied: You cannot modify another driver's operational profile.");
            }
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Token parsing warning during ownership check: {}", ex.getMessage());
        }
    }
}
