package com.ridelink.driver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridelink.driver.dto.*;
import com.ridelink.driver.exception.DriverNotFoundException;
import com.ridelink.driver.exception.GlobalExceptionHandler;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.VehicleType;
import com.ridelink.driver.security.JwtService;
import com.ridelink.driver.service.DriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private DriverService driverService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private DriverController driverController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(driverController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/drivers - Returns 201 Created on valid driver registration")
    void testRegisterDriver_Success() throws Exception {
        DriverRegisterRequest request = DriverRegisterRequest.builder()
                .driverId("user-101")
                .licenseNumber("B1234567")
                .serviceArea("Colombo")
                .vehicle(VehicleRequest.builder()
                        .make("Toyota")
                        .model("Prius")
                        .year(2021)
                        .licensePlate("CAB-1234")
                        .color("White")
                        .vehicleType(VehicleType.CAR)
                        .seatingCapacity(4)
                        .build())
                .build();

        DriverResponse mockResponse = DriverResponse.builder()
                .id("doc-001")
                .driverId("user-101")
                .status(DriverStatus.OFFLINE)
                .serviceArea("Colombo")
                .build();

        when(driverService.registerDriver(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value("user-101"))
                .andExpect(jsonPath("$.status").value("OFFLINE"));
    }

    @Test
    @DisplayName("POST /api/drivers - Returns 400 Bad Request on invalid input")
    void testRegisterDriver_ValidationError() throws Exception {
        DriverRegisterRequest invalidRequest = DriverRegisterRequest.builder()
                .driverId("")
                .serviceArea("")
                .build();

        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("GET /api/drivers/{driverId} - Returns 200 OK when driver exists")
    void testGetDriverById_Success() throws Exception {
        DriverResponse mockResponse = DriverResponse.builder()
                .id("doc-001")
                .driverId("user-101")
                .status(DriverStatus.AVAILABLE)
                .serviceArea("Colombo")
                .build();

        when(driverService.getDriverById("user-101")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/drivers/user-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("user-101"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/drivers/{driverId} - Returns 404 Not Found when absent")
    void testGetDriverById_NotFound() throws Exception {
        when(driverService.getDriverById("user-999"))
                .thenThrow(new DriverNotFoundException("Driver profile not found"));

        mockMvc.perform(get("/api/drivers/user-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("PATCH /api/drivers/{driverId}/availability - Returns 200 OK")
    void testUpdateAvailability_Success() throws Exception {
        AvailabilityUpdateRequest request = new AvailabilityUpdateRequest(DriverStatus.AVAILABLE);

        DriverResponse mockResponse = DriverResponse.builder()
                .driverId("user-101")
                .status(DriverStatus.AVAILABLE)
                .build();

        when(driverService.updateAvailability(eq("user-101"), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/drivers/user-101/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("PATCH /api/drivers/{driverId}/location - Returns 200 OK on valid GPS coordinates")
    void testUpdateLocation_Success() throws Exception {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(6.9271)
                .longitude(79.8612)
                .addressName("Colombo Fort")
                .build();

        DriverResponse mockResponse = DriverResponse.builder()
                .driverId("user-101")
                .build();

        when(driverService.updateLocation(eq("user-101"), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/drivers/user-101/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/drivers/available - Returns 200 OK with eligible drivers")
    void testGetAvailableDrivers_Success() throws Exception {
        AvailableDriverResponse driver = AvailableDriverResponse.builder()
                .driverId("user-101")
                .serviceArea("Colombo")
                .status(DriverStatus.AVAILABLE)
                .build();

        when(driverService.getEligibleAvailableDrivers("Colombo", VehicleType.CAR))
                .thenReturn(List.of(driver));

        mockMvc.perform(get("/api/drivers/available")
                        .param("serviceArea", "Colombo")
                        .param("vehicleType", "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("user-101"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/drivers/nearby - Returns 200 OK with proximity results")
    void testGetNearbyDrivers_Success() throws Exception {
        AvailableDriverResponse driver = AvailableDriverResponse.builder()
                .driverId("user-101")
                .distanceKm(1.85)
                .build();

        when(driverService.getNearbyAvailableDrivers(6.9271, 79.8612, 5.0, VehicleType.CAR))
                .thenReturn(List.of(driver));

        mockMvc.perform(get("/api/drivers/nearby")
                        .param("latitude", "6.9271")
                        .param("longitude", "79.8612")
                        .param("radiusKm", "5.0")
                        .param("vehicleType", "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("user-101"))
                .andExpect(jsonPath("$[0].distanceKm").value(1.85));
    }

    // ─── Vehicle Deletion Controller Tests ──────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/drivers/{driverId}/vehicle - Returns 204 No Content on success")
    void testDeleteVehicle_Success() throws Exception {
        doNothing().when(driverService).deleteVehicle("user-101");

        mockMvc.perform(delete("/api/drivers/user-101/vehicle"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/drivers/{driverId}/vehicle - Negative Scenario: Returns 403 Forbidden when deleting another driver's vehicle")
    void testDeleteVehicle_AnotherUserVehicle_Forbidden() throws Exception {
        String token = "mock-token-for-user-999";
        when(jwtService.extractRole(token)).thenReturn("DRIVER");
        when(jwtService.extractUserId(token)).thenReturn("user-999");

        mockMvc.perform(delete("/api/drivers/user-101/vehicle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied: You cannot modify another driver's operational profile."));
    }

    @Test
    @DisplayName("DELETE /api/drivers/{driverId}/vehicle - Negative Scenario: Returns 404 Not Found when no vehicle exists")
    void testDeleteVehicle_VehicleNotFound() throws Exception {
        doThrow(new com.ridelink.driver.exception.VehicleNotFoundException("Driver does not have a registered vehicle"))
                .when(driverService).deleteVehicle("user-101");

        mockMvc.perform(delete("/api/drivers/user-101/vehicle"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Vehicle Not Found"));
    }

    @Test
    @DisplayName("DELETE /api/drivers/{driverId}/vehicle - Negative Scenario: Returns 400 Bad Request when driver is BUSY")
    void testDeleteVehicle_BusyDriver_BadRequest() throws Exception {
        doThrow(new com.ridelink.driver.exception.InvalidStatusTransitionException("Cannot delete vehicle while driver is currently on an active ride (BUSY status)"))
                .when(driverService).deleteVehicle("user-101");

        mockMvc.perform(delete("/api/drivers/user-101/vehicle"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid Status Transition"));
    }

    // ─── Vehicle Sub-resource Controller Tests ──────────────────────────────────

    @Test
    @DisplayName("POST /api/drivers/{driverId}/vehicle - Returns 201 Created on adding vehicle")
    void testAddVehicle_Success() throws Exception {
        VehicleRequest request = VehicleRequest.builder()
                .make("Honda")
                .model("Fit")
                .year(2022)
                .licensePlate("WP-CAD-8899")
                .color("Blue")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        DriverResponse mockResponse = DriverResponse.builder()
                .driverId("user-101")
                .vehicle(request.toEntity())
                .build();

        when(driverService.addVehicle(eq("user-101"), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/drivers/user-101/vehicle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value("user-101"))
                .andExpect(jsonPath("$.vehicle.licensePlate").value("WP-CAD-8899"));
    }

    @Test
    @DisplayName("GET /api/drivers/{driverId}/vehicle - Returns 200 OK with vehicle details")
    void testGetVehicle_Success() throws Exception {
        com.ridelink.driver.model.Vehicle mockVehicle = com.ridelink.driver.model.Vehicle.builder()
                .make("Toyota")
                .model("Prius")
                .year(2021)
                .licensePlate("CAB-1234")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        when(driverService.getVehicle("user-101")).thenReturn(mockVehicle);

        mockMvc.perform(get("/api/drivers/user-101/vehicle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Toyota"))
                .andExpect(jsonPath("$.licensePlate").value("CAB-1234"));
    }
}
