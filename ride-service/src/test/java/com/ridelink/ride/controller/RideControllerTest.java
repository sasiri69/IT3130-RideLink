package com.ridelink.ride.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.exception.InvalidStateTransitionException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.model.VehicleType;
import com.ridelink.ride.security.JwtService;
import com.ridelink.ride.service.RideService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RideControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private RideService rideService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RideController rideController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(rideController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/rides - Returns 201 Created on valid booking request")
    void testCreateRide_Success() throws Exception {
        RideRequest request = RideRequest.builder()
                .passengerId("user-passenger-001")
                .pickupLocation(LocationPointRequest.builder().latitude(6.9271).longitude(79.8612).addressName("Colombo Fort").build())
                .destinationLocation(LocationPointRequest.builder().latitude(6.9147).longitude(79.9733).addressName("Malabe").build())
                .vehicleType(VehicleType.CAR)
                .serviceArea("Colombo")
                .build();

        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("user-passenger-001")
                .status(RideStatus.REQUESTED)
                .estimatedFare(1500.0)
                .build();

        when(rideService.createRide(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").value("RIDE-A1B2C3D4"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("POST /api/rides - Returns 400 Bad Request on missing fields")
    void testCreateRide_ValidationError() throws Exception {
        RideRequest invalidRequest = RideRequest.builder()
                .passengerId("")
                .serviceArea("")
                .build();

        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("GET /api/rides/{rideId} - Returns 200 OK when ride exists")
    void testGetRideById_Success() throws Exception {
        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .status(RideStatus.REQUESTED)
                .build();

        when(rideService.getRideById("RIDE-A1B2C3D4")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/rides/RIDE-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId").value("RIDE-A1B2C3D4"));
    }

    @Test
    @DisplayName("GET /api/rides/{rideId} - Returns 404 Not Found when ride is absent")
    void testGetRideById_NotFound() throws Exception {
        when(rideService.getRideById("RIDE-9999"))
                .thenThrow(new RideNotFoundException("Ride not found"));

        mockMvc.perform(get("/api/rides/RIDE-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/assign-driver - Returns 200 OK")
    void testAssignDriver_Success() throws Exception {
        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .driverId("user-driver-101")
                .status(RideStatus.ASSIGNED)
                .build();

        when(rideService.assignDriver(eq("RIDE-A1B2C3D4"), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/assign-driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverId").value("user-driver-101"));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/accept - Returns 200 OK")
    void testAcceptRide_Success() throws Exception {
        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .status(RideStatus.ACCEPTED)
                .build();

        when(rideService.acceptRide(eq("RIDE-A1B2C3D4"), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/start - Returns 200 OK")
    void testStartRide_Success() throws Exception {
        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .status(RideStatus.IN_PROGRESS)
                .build();

        when(rideService.startRide(eq("RIDE-A1B2C3D4"), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/complete - Returns 200 OK")
    void testCompleteRide_Success() throws Exception {
        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .status(RideStatus.COMPLETED)
                .finalFare(1650.0)
                .build();

        when(rideService.completeRide(eq("RIDE-A1B2C3D4"), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalFare").value(1650.0));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/cancel - Returns 200 OK")
    void testCancelRide_Success() throws Exception {
        CancelRideRequest req = new CancelRideRequest("Change of schedule");
        RideResponse mockResponse = RideResponse.builder()
                .rideId("RIDE-A1B2C3D4")
                .status(RideStatus.CANCELLED)
                .cancellationReason("Change of schedule")
                .build();

        when(rideService.cancelRide(eq("RIDE-A1B2C3D4"), any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/cancel - Returns 400 Bad Request on illegal transition")
    void testCancelRide_InvalidState_BadRequest() throws Exception {
        CancelRideRequest req = new CancelRideRequest("Want to stop");

        when(rideService.cancelRide(eq("RIDE-A1B2C3D4"), any(), any(), any()))
                .thenThrow(new InvalidStateTransitionException("Cannot cancel a ride that is already IN_PROGRESS."));

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid State Transition"));
    }

    @Test
    @DisplayName("GET /api/rides/passenger/{passengerId} - Returns 200 OK with ride history list")
    void testGetRidesByPassenger_Success() throws Exception {
        java.util.List<RideResponse> list = java.util.List.of(
                RideResponse.builder().rideId("RIDE-AAA").passengerId("user-passenger-001").status(RideStatus.COMPLETED).build(),
                RideResponse.builder().rideId("RIDE-BBB").passengerId("user-passenger-001").status(RideStatus.CANCELLED).build()
        );

        when(rideService.getRidesByPassenger("user-passenger-001")).thenReturn(list);

        mockMvc.perform(get("/api/rides/passenger/user-passenger-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rideId").value("RIDE-AAA"));
    }

    @Test
    @DisplayName("GET /api/rides/driver/{driverId} - Returns 200 OK with assigned rides")
    void testGetRidesByDriver_Success() throws Exception {
        java.util.List<RideResponse> list = java.util.List.of(
                RideResponse.builder().rideId("RIDE-CCC").driverId("user-driver-101").status(RideStatus.IN_PROGRESS).build()
        );

        when(rideService.getRidesByDriver("user-driver-101")).thenReturn(list);

        mockMvc.perform(get("/api/rides/driver/user-driver-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].driverId").value("user-driver-101"));
    }

    @Test
    @DisplayName("PATCH /api/rides/{rideId}/assign-driver - Negative Scenario: Returns 400 on invalid state")
    void testAssignDriver_InvalidState_BadRequest() throws Exception {
        when(rideService.assignDriver(eq("RIDE-A1B2C3D4"), any(), any()))
                .thenThrow(new InvalidStateTransitionException("Cannot assign driver to ride in status: IN_PROGRESS."));

        mockMvc.perform(patch("/api/rides/RIDE-A1B2C3D4/assign-driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid State Transition"));
    }
}
