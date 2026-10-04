package com.ridelink.ride.controller;

import com.ridelink.ride.dto.AssignDriverRequest;
import com.ridelink.ride.dto.CancelRideRequest;
import com.ridelink.ride.dto.RideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.dto.UpdateRidePaymentRequest;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.security.JwtService;
import com.ridelink.ride.service.RideService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing Ride Management Service endpoints.
 */
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ride Management Service", description = "Endpoints for ride booking requests, driver assignment, and lifecycle state management")
public class RideController {

    private final RideService rideService;
    private final JwtService jwtService;

    // ─── 1. Create Ride Request ────────────────────────────────────────────────

    /**
     * Creates a new ride booking request.
     *
     * @param request Ride booking details
     * @param authHeader Bearer authorization header
     * @return Created ride response with HTTP 201 Created status
     */
    @PostMapping
    @Operation(summary = "Create a new ride request")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ride created successfully in REQUESTED state"),
            @ApiResponse(responseCode = "400", description = "Invalid booking details or inactive passenger account"),
            @ApiResponse(responseCode = "404", description = "Passenger account not found"),
            @ApiResponse(responseCode = "503", description = "Account Service unavailable")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RideResponse> createRide(
            @Valid @RequestBody RideRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validatePassengerOwnershipOrAdmin(request.getPassengerId(), authHeader);
        log.info("REST POST /api/rides - Creating ride for passenger: {}", request.getPassengerId());
        RideResponse response = rideService.createRide(request, authHeader);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─── 2. Get Ride Details ───────────────────────────────────────────────────

    /**
     * Retrieves ride details by public rideId.
     *
     * @param rideId Unique ride identifier
     * @return Ride response with HTTP 200 OK status
     */
    @GetMapping("/{rideId}")
    @Operation(summary = "Get ride details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride found"),
            @ApiResponse(responseCode = "404", description = "Ride not found")
    })
    public ResponseEntity<RideResponse> getRideById(
            @Parameter(description = "Public ride ID (e.g. RIDE-XXXX)") @PathVariable String rideId
    ) {
        return ResponseEntity.ok(rideService.getRideById(rideId));
    }

    // ─── 3. Passenger Ride History ─────────────────────────────────────────────

    /**
     * Lists ride booking history for a specific passenger.
     *
     * @param passengerId Target passenger account ID
     * @return List of matching rides
     */
    @GetMapping("/passenger/{passengerId}")
    @Operation(summary = "Get ride history for a passenger")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride history returned")
    })
    public ResponseEntity<List<RideResponse>> getRidesByPassenger(@PathVariable String passengerId) {
        return ResponseEntity.ok(rideService.getRidesByPassenger(passengerId));
    }

    // ─── 4. Driver Assigned Rides ──────────────────────────────────────────────

    /**
     * Lists assigned rides for a specific driver.
     *
     * @param driverId Target driver account ID
     * @return List of matching rides
     */
    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get assigned rides for a driver")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assigned rides returned")
    })
    public ResponseEntity<List<RideResponse>> getRidesByDriver(@PathVariable String driverId) {
        return ResponseEntity.ok(rideService.getRidesByDriver(driverId));
    }

    // ─── 5. Driver Assignment ──────────────────────────────────────────────────

    /**
     * Assigns an available driver to a ride booking.
     * Queries Driver Service if specific driverId is omitted.
     *
     * @param rideId Target ride ID
     * @param request Assignment payload
     * @param authHeader Bearer token
     * @return Updated ride response in ASSIGNED state
     */
    @PatchMapping("/{rideId}/assign-driver")
    @Operation(summary = "Assign an eligible driver to ride (auto or manual)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver assigned successfully in ASSIGNED state"),
            @ApiResponse(responseCode = "400", description = "Invalid ride state for driver assignment"),
            @ApiResponse(responseCode = "404", description = "Ride not found or no eligible drivers available")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RideResponse> assignDriver(
            @PathVariable String rideId,
            @RequestBody(required = false) AssignDriverRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        return ResponseEntity.ok(rideService.assignDriver(rideId, request, authHeader));
    }

    // ─── 6. Driver Acceptance ──────────────────────────────────────────────────

    /**
     * Driver confirms acceptance of the assigned ride.
     *
     * @param rideId Target ride ID
     * @param authHeader Bearer token
     * @return Updated ride response in ACCEPTED state
     */
    @PatchMapping("/{rideId}/accept")
    @Operation(summary = "Driver accepts assigned ride")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride accepted in ACCEPTED state"),
            @ApiResponse(responseCode = "400", description = "Invalid ride state transition"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the assigned driver"),
            @ApiResponse(responseCode = "404", description = "Ride not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RideResponse> acceptRide(
            @PathVariable String rideId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String driverId = extractUserIdFromHeader(authHeader);
        return ResponseEntity.ok(rideService.acceptRide(rideId, driverId, authHeader));
    }

    // ─── 7. Start Ride ─────────────────────────────────────────────────────────

    /**
     * Driver starts the trip.
     *
     * @param rideId Target ride ID
     * @param authHeader Bearer token
     * @return Updated ride response in IN_PROGRESS state
     */
    @PatchMapping("/{rideId}/start")
    @Operation(summary = "Driver starts the trip")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride started in IN_PROGRESS state"),
            @ApiResponse(responseCode = "400", description = "Cannot start ride before acceptance"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the assigned driver"),
            @ApiResponse(responseCode = "404", description = "Ride not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RideResponse> startRide(
            @PathVariable String rideId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String driverId = extractUserIdFromHeader(authHeader);
        return ResponseEntity.ok(rideService.startRide(rideId, driverId, authHeader));
    }

    // ─── 8. Complete Ride ──────────────────────────────────────────────────────

    /**
     * Driver completes the trip at destination.
     *
     * @param rideId Target ride ID
     * @param authHeader Bearer token
     * @return Updated ride response in COMPLETED state
     */
    @PatchMapping("/{rideId}/complete")
    @Operation(summary = "Driver completes the trip")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride completed in COMPLETED state"),
            @ApiResponse(responseCode = "400", description = "Cannot complete ride that is not in progress"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Caller is not the assigned driver"),
            @ApiResponse(responseCode = "404", description = "Ride not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RideResponse> completeRide(
            @PathVariable String rideId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String driverId = extractUserIdFromHeader(authHeader);
        return ResponseEntity.ok(rideService.completeRide(rideId, driverId, authHeader));
    }

    // ─── 9. Cancel Ride ────────────────────────────────────────────────────────

    /**
     * Cancels a ride booking before trip has commenced.
     *
     * @param rideId Target ride ID
     * @param request Cancellation payload containing reason
     * @param authHeader Bearer token
     * @return Updated ride response in CANCELLED state
     */
    @PatchMapping("/{rideId}/cancel")
    @Operation(summary = "Cancel a ride booking")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride cancelled in CANCELLED state"),
            @ApiResponse(responseCode = "400", description = "Cannot cancel an in-progress or completed ride"),
            @ApiResponse(responseCode = "404", description = "Ride not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RideResponse> cancelRide(
            @PathVariable String rideId,
            @Valid @RequestBody CancelRideRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        validateCancelOwnershipOrAdmin(rideId, authHeader);
        String cancelledBy = extractUserIdFromHeader(authHeader);
        return ResponseEntity.ok(rideService.cancelRide(rideId, request, cancelledBy, authHeader));
    }

    // ─── 10. Admin Ride Listing ────────────────────────────────────────────────

    /**
     * Lists all rides with optional status filtering.
     * Requires ADMIN role.
     *
     * @param status Optional ride status filter
     * @return List of matching rides
     */
    @GetMapping
    @Operation(summary = "List all rides with optional status filter (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of rides returned"),
            @ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required")
    })
    public ResponseEntity<List<RideResponse>> getAllRides(
            @RequestParam(required = false) RideStatus status
    ) {
        return ResponseEntity.ok(rideService.getAllRides(status));
    }

    // ─── 11. Ride Payment Status Callback ──────────────────────────────────────

    /**
     * Updates payment status on a completed ride booking.
     * Called by Payment Service upon successful payment recording.
     *
     * @param rideId Target ride ID
     * @param request Payment update payload
     * @return Updated ride response with payment details
     */
    @PatchMapping("/{rideId}/payment")
    @Operation(summary = "Update ride payment status (called upon payment completion)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride payment status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid ride state for payment (must be COMPLETED)"),
            @ApiResponse(responseCode = "404", description = "Ride not found")
    })
    public ResponseEntity<RideResponse> updatePaymentStatus(
            @PathVariable String rideId,
            @Valid @RequestBody UpdateRidePaymentRequest request
    ) {
        log.info("REST PATCH /api/rides/{}/payment - Recording paymentId: {}", rideId, request.getPaymentId());
        return ResponseEntity.ok(rideService.updatePaymentStatus(rideId, request));
    }

    private String extractUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtService.extractUserId(authHeader.substring(7));
        } catch (Exception ex) {
            return null;
        }
    }

    private void validatePassengerOwnershipOrAdmin(String passengerId, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        try {
            String role = jwtService.extractRole(token);
            String tokenUserId = jwtService.extractUserId(token);
            if (!"ADMIN".equalsIgnoreCase(role) && (tokenUserId != null && !tokenUserId.equalsIgnoreCase(passengerId))) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access denied: You cannot create ride bookings for another passenger's account."
                );
            }
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Token parsing warning during passenger check: {}", ex.getMessage());
        }
    }

    private void validateCancelOwnershipOrAdmin(String rideId, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        try {
            String role = jwtService.extractRole(token);
            String tokenUserId = jwtService.extractUserId(token);
            if (!"ADMIN".equalsIgnoreCase(role) && tokenUserId != null) {
                RideResponse ride = rideService.getRideById(rideId);
                boolean isPassenger = tokenUserId.equalsIgnoreCase(ride.getPassengerId());
                boolean isDriver = ride.getDriverId() != null && tokenUserId.equalsIgnoreCase(ride.getDriverId());
                if (!isPassenger && !isDriver) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Access denied: Only the booking passenger, assigned driver, or admin can cancel this ride."
                    );
                }
            }
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Token parsing warning during cancel check: {}", ex.getMessage());
        }
    }
}
