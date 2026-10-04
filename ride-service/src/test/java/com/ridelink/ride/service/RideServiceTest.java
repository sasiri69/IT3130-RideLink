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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private AccountServiceClient accountServiceClient;

    @InjectMocks
    private RideService rideService;

    private RideRequest validRideRequest;
    private AccountUserResponse activePassenger;
    private Ride existingRide;

    @BeforeEach
    void setUp() {
        LocationPointRequest pickupReq = LocationPointRequest.builder()
                .latitude(6.9271)
                .longitude(79.8612)
                .addressName("Colombo Fort")
                .build();

        LocationPointRequest destReq = LocationPointRequest.builder()
                .latitude(6.9147)
                .longitude(79.9733)
                .addressName("Malabe Junction")
                .build();

        validRideRequest = RideRequest.builder()
                .passengerId("user-passenger-001")
                .pickupLocation(pickupReq)
                .destinationLocation(destReq)
                .vehicleType(VehicleType.CAR)
                .serviceArea("Colombo")
                .build();

        activePassenger = AccountUserResponse.builder()
                .id("user-passenger-001")
                .role("PASSENGER")
                .status("ACTIVE")
                .build();

        existingRide = Ride.builder()
                .id("doc-001")
                .rideId("RIDE-A1B2C3D4")
                .passengerId("user-passenger-001")
                .pickupLocation(pickupReq.toEntity())
                .destinationLocation(destReq.toEntity())
                .serviceArea("Colombo")
                .vehicleType(VehicleType.CAR)
                .status(RideStatus.REQUESTED)
                .distanceKm(12.5)
                .estimatedFare(1750.0)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    // ─── 1. Creation Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Should successfully create a ride in REQUESTED state")
    void testCreateRide_Success() {
        when(accountServiceClient.getPassengerById("user-passenger-001", null)).thenReturn(activePassenger);
        when(rideRepository.save(any(Ride.class))).thenReturn(existingRide);

        RideResponse response = rideService.createRide(validRideRequest, null);

        assertThat(response).isNotNull();
        assertThat(response.getRideId()).isEqualTo("RIDE-A1B2C3D4");
        assertThat(response.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.getEstimatedFare()).isPositive();
        assertThat(response.getLinks()).containsKey("self");
        assertThat(response.getLinks()).containsKey("assignDriver");

        verify(accountServiceClient, times(1)).getPassengerById("user-passenger-001", null);
        verify(rideRepository, times(1)).save(any(Ride.class));
    }

    @Test
    @DisplayName("Negative Scenario: Should throw exception if passenger account is suspended or inactive")
    void testCreateRide_InactivePassenger_ThrowsException() {
        AccountUserResponse inactivePassenger = AccountUserResponse.builder()
                .id("user-passenger-001")
                .role("PASSENGER")
                .status("SUSPENDED")
                .build();

        when(accountServiceClient.getPassengerById("user-passenger-001", null)).thenReturn(inactivePassenger);

        assertThatThrownBy(() -> rideService.createRide(validRideRequest, null))
                .isInstanceOf(InvalidPassengerAccountException.class)
                .hasMessageContaining("is not active");

        verify(rideRepository, never()).save(any());
    }

    // ─── 2. Driver Assignment Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Should auto-assign first available driver from Driver Service")
    void testAssignDriver_AutoSelect_Success() {
        AvailableDriverResponse availableDriver = AvailableDriverResponse.builder()
                .driverId("user-driver-101")
                .serviceArea("Colombo")
                .status("AVAILABLE")
                .build();

        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(driverServiceClient.getAvailableDrivers("Colombo", VehicleType.CAR)).thenReturn(List.of(availableDriver));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.assignDriver("RIDE-A1B2C3D4", null, null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(response.getDriverId()).isEqualTo("user-driver-101");
        assertThat(response.getAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should assign specified driver when driverId is explicitly provided")
    void testAssignDriver_Manual_Success() {
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignDriverRequest request = new AssignDriverRequest("driver-custom-888");
        RideResponse response = rideService.assignDriver("RIDE-A1B2C3D4", request, null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(response.getDriverId()).isEqualTo("driver-custom-888");
        verify(driverServiceClient, never()).getAvailableDrivers(any(), any());
    }

    @Test
    @DisplayName("Negative Scenario: Should throw NoAvailableDriverException when no driver is available")
    void testAssignDriver_NoAvailableDriver_ThrowsException() {
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(driverServiceClient.getAvailableDrivers("Colombo", VehicleType.CAR)).thenReturn(List.of());

        assertThatThrownBy(() -> rideService.assignDriver("RIDE-A1B2C3D4", null, null))
                .isInstanceOf(NoAvailableDriverException.class)
                .hasMessageContaining("No available drivers currently found");
    }

    @Test
    @DisplayName("Negative Scenario: Should throw InvalidStateTransitionException if ride is not in REQUESTED status")
    void testAssignDriver_InvalidState_ThrowsException() {
        existingRide.setStatus(RideStatus.IN_PROGRESS);
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        assertThatThrownBy(() -> rideService.assignDriver("RIDE-A1B2C3D4", null, null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot assign driver to ride in status: IN_PROGRESS");
    }

    // ─── 3. Driver Acceptance Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Should accept assigned ride and update driver status to BUSY")
    void testAcceptRide_Success() {
        existingRide.setStatus(RideStatus.ASSIGNED);
        existingRide.setDriverId("user-driver-101");

        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.acceptRide("RIDE-A1B2C3D4", "user-driver-101", null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(response.getAcceptedAt()).isNotNull();

        verify(driverServiceClient, times(1)).updateDriverAvailability("user-driver-101", "BUSY", null);
    }

    @Test
    @DisplayName("Negative Scenario: Another driver cannot accept a ride not assigned to them")
    void testAcceptRide_WrongDriver_ThrowsException() {
        existingRide.setStatus(RideStatus.ASSIGNED);
        existingRide.setDriverId("user-driver-101");

        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        assertThatThrownBy(() -> rideService.acceptRide("RIDE-A1B2C3D4", "driver-intruder-999", null))
                .isInstanceOf(UnauthorizedRideAccessException.class)
                .hasMessageContaining("is not the assigned driver");
    }

    // ─── 4. Start & Complete Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Should start accepted ride into IN_PROGRESS state")
    void testStartRide_Success() {
        existingRide.setStatus(RideStatus.ACCEPTED);
        existingRide.setDriverId("user-driver-101");

        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.startRide("RIDE-A1B2C3D4", "user-driver-101", null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(response.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Negative Scenario: Cannot start ride if not yet accepted")
    void testStartRide_InvalidState_ThrowsException() {
        existingRide.setStatus(RideStatus.ASSIGNED);
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        assertThatThrownBy(() -> rideService.startRide("RIDE-A1B2C3D4", "user-driver-101", null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot start ride in status: ASSIGNED");
    }

    @Test
    @DisplayName("Should complete trip, calculate final fare, and release driver to AVAILABLE")
    void testCompleteRide_Success() {
        existingRide.setStatus(RideStatus.IN_PROGRESS);
        existingRide.setDriverId("user-driver-101");

        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        RideResponse response = rideService.completeRide("RIDE-A1B2C3D4", "user-driver-101", null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(response.getCompletedAt()).isNotNull();
        assertThat(response.getFinalFare()).isEqualTo(existingRide.getEstimatedFare());

        verify(driverServiceClient, times(1)).updateDriverAvailability("user-driver-101", "AVAILABLE", null);
    }

    // ─── 5. Cancellation Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should cancel ride in REQUESTED state without driver release")
    void testCancelRide_FromRequested_Success() {
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelRideRequest req = new CancelRideRequest("Passenger changed plans");
        RideResponse response = rideService.cancelRide("RIDE-A1B2C3D4", req, "user-passenger-001", null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.getCancellationReason()).isEqualTo("Passenger changed plans");
        verify(driverServiceClient, never()).updateDriverAvailability(any(), any(), any());
    }

    @Test
    @DisplayName("Should cancel ride in ACCEPTED state and release driver back to AVAILABLE")
    void testCancelRide_FromAccepted_ReleasesDriver_Success() {
        existingRide.setStatus(RideStatus.ACCEPTED);
        existingRide.setDriverId("user-driver-101");

        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelRideRequest req = new CancelRideRequest("Driver flat tire");
        RideResponse response = rideService.cancelRide("RIDE-A1B2C3D4", req, "user-driver-101", null);

        assertThat(response.getStatus()).isEqualTo(RideStatus.CANCELLED);
        verify(driverServiceClient, times(1)).updateDriverAvailability("user-driver-101", "AVAILABLE", null);
    }

    @Test
    @DisplayName("Negative Scenario: Cannot cancel an in-progress ride")
    void testCancelRide_InProgressRide_ThrowsException() {
        existingRide.setStatus(RideStatus.IN_PROGRESS);
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        CancelRideRequest req = new CancelRideRequest("Want to stop");

        assertThatThrownBy(() -> rideService.cancelRide("RIDE-A1B2C3D4", req, "user-passenger-001", null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot cancel a ride that is already IN_PROGRESS");
    }

    @Test
    @DisplayName("Negative Scenario: Cannot cancel an already completed ride")
    void testCancelRide_CompletedRide_ThrowsException() {
        existingRide.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        CancelRideRequest req = new CancelRideRequest("Want refund");

        assertThatThrownBy(() -> rideService.cancelRide("RIDE-A1B2C3D4", req, "user-passenger-001", null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot cancel a ride that is already COMPLETED");
    }

    // ─── 6. Query Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should retrieve ride details by ID")
    void testGetRideById_Success() {
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        RideResponse response = rideService.getRideById("RIDE-A1B2C3D4");

        assertThat(response).isNotNull();
        assertThat(response.getRideId()).isEqualTo("RIDE-A1B2C3D4");
    }

    @Test
    @DisplayName("Negative Scenario: Throws RideNotFoundException when ride ID is absent")
    void testGetRideById_NotFound_ThrowsException() {
        when(rideRepository.findByRideId("RIDE-9999")).thenReturn(Optional.empty());
        when(rideRepository.findById("RIDE-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.getRideById("RIDE-9999"))
                .isInstanceOf(RideNotFoundException.class)
                .hasMessageContaining("was not found");
    }

    // ─── 7. Payment Status & Validation Tests ──────────────────────────────────

    @Test
    @DisplayName("Should successfully record payment on COMPLETED ride")
    void testUpdatePaymentStatus_Success() {
        existingRide.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRidePaymentRequest request = UpdateRidePaymentRequest.builder()
                .paymentId("PAY-12345678")
                .paymentStatus("PAID")
                .build();

        RideResponse response = rideService.updatePaymentStatus("RIDE-A1B2C3D4", request);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo("PAY-12345678");
        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
        assertThat(response.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("Negative Scenario: Should throw InvalidStateTransitionException when updating payment on non-completed ride")
    void testUpdatePaymentStatus_NotCompleted_ThrowsException() {
        existingRide.setStatus(RideStatus.IN_PROGRESS);
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        UpdateRidePaymentRequest request = UpdateRidePaymentRequest.builder()
                .paymentId("PAY-12345678")
                .paymentStatus("PAID")
                .build();

        assertThatThrownBy(() -> rideService.updatePaymentStatus("RIDE-A1B2C3D4", request))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Ride must be in COMPLETED state");
    }

    @Test
    @DisplayName("Negative Scenario: Should throw InvalidStateTransitionException on invalid driverId format during assignment")
    void testAssignDriver_InvalidDriverIdFormat_ThrowsException() {
        when(rideRepository.findByRideId("RIDE-A1B2C3D4")).thenReturn(Optional.of(existingRide));

        AssignDriverRequest request = new AssignDriverRequest("@invalid#driver!");

        assertThatThrownBy(() -> rideService.assignDriver("RIDE-A1B2C3D4", request, null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid driver ID format");
    }
}
