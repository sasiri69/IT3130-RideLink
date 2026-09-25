package com.ridelink.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.FareEstimateResponse;
import com.ridelink.payment.model.VehicleType;
import com.ridelink.payment.security.JwtService;
import com.ridelink.payment.service.FareCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FareController.class)
@AutoConfigureMockMvc(addFilters = false)
class FareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FareCalculationService fareCalculationService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/fares/estimate should return 200 OK with fare breakdown")
    void testGetFareEstimate_Success() throws Exception {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.CAR)
                .directDistanceKm(10.0)
                .surgeMultiplier(1.0)
                .build();

        FareEstimateResponse response = FareEstimateResponse.builder()
                .vehicleType(VehicleType.CAR)
                .distanceKm(10.0)
                .baseFare(350.0)
                .ratePerKm(100.0)
                .distanceFare(1000.0)
                .surgeMultiplier(1.0)
                .serviceFee(67.5)
                .estimatedFare(1417.5)
                .currency("LKR")
                .build();

        when(fareCalculationService.calculateFareEstimate(any())).thenReturn(response);

        mockMvc.perform(post("/api/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("CAR"))
                .andExpect(jsonPath("$.distanceKm").value(10.0))
                .andExpect(jsonPath("$.estimatedFare").value(1417.5));
    }

    @Test
    @DisplayName("POST /api/fares/estimate with missing vehicle type should return 400 Bad Request")
    void testGetFareEstimate_ValidationFailure() throws Exception {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .directDistanceKm(10.0)
                .build(); // vehicleType is null

        mockMvc.perform(post("/api/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
