package com.ridelink.driver.service;

import com.ridelink.driver.client.AccountServiceClient;
import com.ridelink.driver.client.dto.AccountUserResponse;
import com.ridelink.driver.dto.*;
import com.ridelink.driver.exception.*;
import com.ridelink.driver.model.*;
import com.ridelink.driver.repository.DriverRepository;
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
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @InjectMocks
    private DriverService driverService;

    private DriverRegisterRequest validRequest;
    private AccountUserResponse validDriverAccount;
    private Driver existingDriver;

    @BeforeEach
    void setUp() {
        VehicleRequest vehicleRequest = VehicleRequest.builder()
                .make("Toyota")
                .model("Prius")
                .year(2021)
                .licensePlate("CAB-1234")
                .color("Pearl White")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        LocationRequest locationRequest = LocationRequest.builder()
                .latitude(6.9271)
                .longitude(79.8612)
                .addressName("Colombo Fort")
                .build();

        validRequest = DriverRegisterRequest.builder()
                .driverId("user-101")
                .licenseNumber("B1234567")
                .serviceArea("Colombo")
                .vehicle(vehicleRequest)
                .initialLocation(locationRequest)
                .build();

        validDriverAccount = AccountUserResponse.builder()
                .id("user-101")
                .firstName("Sunil")
                .lastName("Perera")
                .email("sunil@test.com")
                .role("DRIVER")
                .status("ACTIVE")
                .build();

        existingDriver = Driver.builder()
                .id("doc-001")
                .driverId("user-101")
                .licenseNumber("B1234567")
                .serviceArea("Colombo")
                .status(DriverStatus.OFFLINE)
                .vehicle(vehicleRequest.toEntity())
                .currentLocation(locationRequest.toEntity())
                .rating(5.0)
                .totalRides(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── 1. Registration Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("Should successfully register driver when Account Service verifies valid DRIVER")
    void testRegisterDriver_Success() {
        when(driverRepository.existsByDriverId("user-101")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("B1234567")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("CAB-1234")).thenReturn(false);
        when(accountServiceClient.getUserById("user-101", null)).thenReturn(validDriverAccount);
        when(driverRepository.save(any(Driver.class))).thenReturn(existingDriver);

        DriverResponse response = driverService.registerDriver(validRequest, null);

        assertThat(response).isNotNull();
        assertThat(response.getDriverId()).isEqualTo("user-101");
        assertThat(response.getStatus()).isEqualTo(DriverStatus.OFFLINE);
        assertThat(response.getVehicle().getLicensePlate()).isEqualTo("CAB-1234");
        assertThat(response.getLinks()).containsKey("self");

        verify(accountServiceClient, times(1)).getUserById("user-101", null);
        verify(driverRepository, times(1)).save(any(Driver.class));
    }

    @Test
    @DisplayName("Negative Scenario: Should fail registration when driver is already registered")
    void testRegisterDriver_AlreadyRegistered_ThrowsException() {
        when(driverRepository.existsByDriverId("user-101")).thenReturn(true);

        assertThatThrownBy(() -> driverService.registerDriver(validRequest, null))
                .isInstanceOf(DriverAlreadyRegisteredException.class)
                .hasMessageContaining("has already registered an operational profile");

        verify(accountServiceClient, never()).getUserById(any(), any());
    }

    @Test
    @DisplayName("Negative Scenario: Should fail registration when user account does not exist in Account Service")
    void testRegisterDriver_AccountNotFound_ThrowsException() {
        when(driverRepository.existsByDriverId("user-101")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("B1234567")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("CAB-1234")).thenReturn(false);
        when(accountServiceClient.getUserById("user-101", null))
                .thenThrow(new DriverAccountNotFoundException("User not found"));

        assertThatThrownBy(() -> driverService.registerDriver(validRequest, null))
                .isInstanceOf(DriverAccountNotFoundException.class);
    }

    @Test
    @DisplayName("Negative Scenario: Should reject registration if Account Service role is PASSENGER")
    void testRegisterDriver_WrongRole_ThrowsException() {
        AccountUserResponse passengerUser = AccountUserResponse.builder()
                .id("user-101")
                .role("PASSENGER")
                .status("ACTIVE")
                .build();

        when(driverRepository.existsByDriverId("user-101")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("B1234567")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("CAB-1234")).thenReturn(false);
        when(accountServiceClient.getUserById("user-101", null)).thenReturn(passengerUser);

        assertThatThrownBy(() -> driverService.registerDriver(validRequest, null))
                .isInstanceOf(InvalidDriverAccountException.class)
                .hasMessageContaining("Only users registered with role 'DRIVER' can create a driver operational profile");
    }

    @Test
    @DisplayName("Negative Scenario: Should reject registration if Account Service status is SUSPENDED")
    void testRegisterDriver_SuspendedAccount_ThrowsException() {
        AccountUserResponse suspendedUser = AccountUserResponse.builder()
                .id("user-101")
                .role("DRIVER")
                .status("SUSPENDED")
                .build();

        when(driverRepository.existsByDriverId("user-101")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("B1234567")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("CAB-1234")).thenReturn(false);
        when(accountServiceClient.getUserById("user-101", null)).thenReturn(suspendedUser);

        assertThatThrownBy(() -> driverService.registerDriver(validRequest, null))
                .isInstanceOf(InvalidDriverAccountException.class)
                .hasMessageContaining("Only ACTIVE accounts may register");
    }

    // ─── 2. Availability & Lifecycle Tests ──────────────────────────────────────

    @Test
    @DisplayName("Should successfully update availability status to AVAILABLE")
    void testUpdateAvailability_Success() {
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        AvailabilityUpdateRequest request = new AvailabilityUpdateRequest(DriverStatus.AVAILABLE);
        DriverResponse response = driverService.updateAvailability("user-101", request);

        assertThat(response.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Negative Scenario: Suspended driver cannot declare themselves AVAILABLE")
    void testUpdateAvailability_Suspended_ThrowsException() {
        existingDriver.setStatus(DriverStatus.SUSPENDED);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));

        AvailabilityUpdateRequest request = new AvailabilityUpdateRequest(DriverStatus.AVAILABLE);

        assertThatThrownBy(() -> driverService.updateAvailability("user-101", request))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("cannot declare availability");
    }

    // ─── 3. Location & Vehicle Updates ──────────────────────────────────────────

    @Test
    @DisplayName("Should successfully update simulated GPS location")
    void testUpdateLocation_Success() {
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(6.9147)
                .longitude(79.9733)
                .addressName("Malabe Junction")
                .build();

        DriverResponse response = driverService.updateLocation("user-101", request);

        assertThat(response.getCurrentLocation().getLatitude()).isEqualTo(6.9147);
        assertThat(response.getCurrentLocation().getAddressName()).isEqualTo("Malabe Junction");
    }

    // ─── 4. Dispatching & Proximity Queries ─────────────────────────────────────

    @Test
    @DisplayName("Should retrieve eligible available drivers filtered by area and vehicle type")
    void testGetEligibleAvailableDrivers() {
        existingDriver.setStatus(DriverStatus.AVAILABLE);
        when(driverRepository.findByStatusAndServiceAreaIgnoreCaseAndVehicleVehicleType(
                DriverStatus.AVAILABLE, "Colombo", VehicleType.CAR
        )).thenReturn(List.of(existingDriver));

        List<AvailableDriverResponse> results = driverService.getEligibleAvailableDrivers("Colombo", VehicleType.CAR);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDriverId()).isEqualTo("user-101");
        assertThat(results.get(0).getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should correctly calculate Haversine distance for nearby drivers")
    void testGetNearbyAvailableDrivers_Haversine() {
        existingDriver.setStatus(DriverStatus.AVAILABLE);
        existingDriver.setCurrentLocation(Location.builder()
                .latitude(6.9271)
                .longitude(79.8612)
                .addressName("Colombo Fort")
                .build());

        when(driverRepository.findByStatus(DriverStatus.AVAILABLE)).thenReturn(List.of(existingDriver));

        List<AvailableDriverResponse> nearby = driverService.getNearbyAvailableDrivers(
                6.9117, 79.8510, 5.0, VehicleType.CAR
        );

        assertThat(nearby).hasSize(1);
        assertThat(nearby.get(0).getDriverId()).isEqualTo("user-101");
        assertThat(nearby.get(0).getDistanceKm()).isNotNull();
        assertThat(nearby.get(0).getDistanceKm()).isLessThan(5.0);
    }

    // ─── 5. Vehicle Deletion Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Should successfully delete vehicle and transition AVAILABLE driver to OFFLINE")
    void testDeleteVehicle_Success() {
        existingDriver.setStatus(DriverStatus.AVAILABLE);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        driverService.deleteVehicle("user-101");

        assertThat(existingDriver.getVehicle()).isNull();
        assertThat(existingDriver.getStatus()).isEqualTo(DriverStatus.OFFLINE);
        verify(driverRepository, times(1)).save(existingDriver);
    }

    @Test
    @DisplayName("Negative Scenario: Should fail to delete vehicle when driver has no vehicle registered")
    void testDeleteVehicle_NoVehicle_ThrowsException() {
        existingDriver.setVehicle(null);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));

        assertThatThrownBy(() -> driverService.deleteVehicle("user-101"))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessageContaining("does not currently have a registered vehicle");

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Negative Scenario: Should reject vehicle deletion when driver is on active ride (BUSY status)")
    void testDeleteVehicle_BusyDriver_ThrowsException() {
        existingDriver.setStatus(DriverStatus.BUSY);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));

        assertThatThrownBy(() -> driverService.deleteVehicle("user-101"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot delete vehicle while driver is currently on an active ride (BUSY status)");

        verify(driverRepository, never()).save(any());
    }

    // ─── 6. Vehicle Addition & Retrieval Tests ──────────────────────────────────

    @Test
    @DisplayName("Should successfully add vehicle to driver who has no vehicle")
    void testAddVehicle_Success() {
        existingDriver.setVehicle(null);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));
        when(driverRepository.existsByVehicleLicensePlate("WP-CAD-8899")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleRequest newVehicleReq = VehicleRequest.builder()
                .make("Honda")
                .model("Fit")
                .year(2022)
                .licensePlate("WP-CAD-8899")
                .color("Blue")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        DriverResponse response = driverService.addVehicle("user-101", newVehicleReq);

        assertThat(response.getVehicle()).isNotNull();
        assertThat(response.getVehicle().getLicensePlate()).isEqualTo("WP-CAD-8899");
        verify(driverRepository, times(1)).save(existingDriver);
    }

    @Test
    @DisplayName("Negative Scenario: Should fail to add vehicle when driver already has a vehicle registered")
    void testAddVehicle_AlreadyHasVehicle_ThrowsException() {
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));

        VehicleRequest newVehicleReq = VehicleRequest.builder()
                .make("Honda")
                .model("Fit")
                .year(2022)
                .licensePlate("WP-CAD-8899")
                .color("Blue")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        assertThatThrownBy(() -> driverService.addVehicle("user-101", newVehicleReq))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already has a registered vehicle. Use PUT");

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully retrieve vehicle for driver")
    void testGetVehicle_Success() {
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));

        Vehicle vehicle = driverService.getVehicle("user-101");

        assertThat(vehicle).isNotNull();
        assertThat(vehicle.getLicensePlate()).isEqualTo("CAB-1234");
    }

    // ─── 7. Stats Update (Inter-service) Tests ──────────────────────────────────

    @Test
    @DisplayName("Should update totalRides and recalculate rolling average rating correctly")
    void testUpdateDriverStats_WithRating() {
        existingDriver.setTotalRides(4);
        existingDriver.setRating(4.5);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        DriverStatsUpdateRequest request = DriverStatsUpdateRequest.builder()
                .newRating(5.0)
                .ridesIncrement(1)
                .build();

        DriverResponse response = driverService.updateDriverStats("user-101", request);

        assertThat(response.getTotalRides()).isEqualTo(5);
        // Expected: ((4.5 * 4) + 5.0) / 5 = 23.0 / 5 = 4.6
        assertThat(response.getRating()).isEqualTo(4.6);
    }

    @Test
    @DisplayName("Should increment totalRides only when no rating is provided")
    void testUpdateDriverStats_NoRating_OnlyRidesUpdated() {
        existingDriver.setTotalRides(3);
        existingDriver.setRating(4.0);
        when(driverRepository.findByDriverId("user-101")).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        DriverStatsUpdateRequest request = DriverStatsUpdateRequest.builder()
                .ridesIncrement(1)
                .build(); // newRating is null

        DriverResponse response = driverService.updateDriverStats("user-101", request);

        assertThat(response.getTotalRides()).isEqualTo(4);
        assertThat(response.getRating()).isEqualTo(4.0); // unchanged
    }
}
