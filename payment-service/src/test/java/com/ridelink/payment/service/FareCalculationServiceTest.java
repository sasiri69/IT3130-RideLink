package com.ridelink.payment.service;

import com.ridelink.payment.dto.FareEstimateRequest;
import com.ridelink.payment.dto.FareEstimateResponse;
import com.ridelink.payment.exception.InvalidPaymentRequestException;
import com.ridelink.payment.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FareCalculationServiceTest {

    private FareCalculationService fareCalculationService;

    @BeforeEach
    void setUp() {
        fareCalculationService = new FareCalculationService();
    }

    @Test
    @DisplayName("Should accurately calculate fare estimate using direct distance for CAR")
    void testCalculateFareEstimate_DirectDistance_Car() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.CAR)
                .directDistanceKm(10.0)
                .surgeMultiplier(1.0)
                .build();

        FareEstimateResponse response = fareCalculationService.calculateFareEstimate(request);

        assertNotNull(response);
        assertEquals(VehicleType.CAR, response.getVehicleType());
        assertEquals(10.0, response.getDistanceKm());
        assertEquals(350.0, response.getBaseFare());
        assertEquals(100.0, response.getRatePerKm());
        assertEquals(1000.0, response.getDistanceFare());
        // Subtotal = 1350.0. Service fee (5%) = 67.5. Total = 1417.5
        assertEquals(67.5, response.getServiceFee());
        assertEquals(1417.5, response.getEstimatedFare());
        assertNotNull(response.getLinks().get("self"));
    }

    @Test
    @DisplayName("Should accurately calculate fare estimate with surge multiplier for VAN")
    void testCalculateFareEstimate_SurgeMultiplier_Van() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.VAN)
                .directDistanceKm(15.0)
                .surgeMultiplier(1.5)
                .build();

        FareEstimateResponse response = fareCalculationService.calculateFareEstimate(request);

        assertNotNull(response);
        assertEquals(VehicleType.VAN, response.getVehicleType());
        assertEquals(500.0, response.getBaseFare());
        assertEquals(140.0, response.getRatePerKm());
        assertEquals(2100.0, response.getDistanceFare());
        // Subtotal = 2600.0. Service fee = 130.0. Total = (2600 * 1.5) + 130 = 4030.0
        assertEquals(130.0, response.getServiceFee());
        assertEquals(4030.0, response.getEstimatedFare());
    }

    @Test
    @DisplayName("Should calculate fare estimate using Haversine coordinates")
    void testCalculateFareEstimate_Coordinates() {
        // Colombo Fort to SLIIT Malabe (~13 km)
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.TUKTUK)
                .pickupLatitude(6.9271)
                .pickupLongitude(79.8612)
                .destinationLatitude(6.9147)
                .destinationLongitude(79.9729)
                .surgeMultiplier(1.0)
                .build();

        FareEstimateResponse response = fareCalculationService.calculateFareEstimate(request);

        assertNotNull(response);
        assertTrue(response.getDistanceKm() > 10.0 && response.getDistanceKm() < 15.0);
        assertEquals(200.0, response.getBaseFare());
        assertEquals(70.0, response.getRatePerKm());
        assertTrue(response.getEstimatedFare() > 900.0);
    }

    @Test
    @DisplayName("Should throw exception when direct distance is non-positive")
    void testCalculateFareEstimate_NegativeDistance() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.BIKE)
                .directDistanceKm(-5.0)
                .build();

        assertThrows(InvalidPaymentRequestException.class, () ->
                fareCalculationService.calculateFareEstimate(request)
        );
    }

    @Test
    @DisplayName("Should throw exception when coordinates are identical")
    void testCalculateFareEstimate_IdenticalCoordinates() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.CAR)
                .pickupLatitude(6.9271)
                .pickupLongitude(79.8612)
                .destinationLatitude(6.9271)
                .destinationLongitude(79.8612)
                .build();

        assertThrows(InvalidPaymentRequestException.class, () ->
                fareCalculationService.calculateFareEstimate(request)
        );
    }

    @Test
    @DisplayName("Should throw exception when surge multiplier is out of range")
    void testCalculateFareEstimate_InvalidSurge() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.CAR)
                .directDistanceKm(10.0)
                .surgeMultiplier(4.5)
                .build();

        assertThrows(InvalidPaymentRequestException.class, () ->
                fareCalculationService.calculateFareEstimate(request)
        );
    }

    @Test
    @DisplayName("Should correctly apply BIKE rate card with base fare 150 and 50/km")
    void testCalculateFareEstimate_BikeRateCard() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.BIKE)
                .directDistanceKm(5.0)
                .surgeMultiplier(1.0)
                .build();

        FareEstimateResponse response = fareCalculationService.calculateFareEstimate(request);

        assertEquals(150.0, response.getBaseFare());
        assertEquals(50.0, response.getRatePerKm());
        assertEquals(250.0, response.getDistanceFare()); // 5 * 50
        // Subtotal = 400, service fee 5% = 20, total = 420
        assertEquals(20.0, response.getServiceFee());
        assertEquals(420.0, response.getEstimatedFare());
    }

    @Test
    @DisplayName("Should include documented calculation rule in response (Assignment §5 fare rule requirement)")
    void testCalculateFareEstimate_IncludesCalculationRule() {
        FareEstimateRequest request = FareEstimateRequest.builder()
                .vehicleType(VehicleType.CAR)
                .directDistanceKm(10.0)
                .surgeMultiplier(1.0)
                .build();

        FareEstimateResponse response = fareCalculationService.calculateFareEstimate(request);

        assertNotNull(response.getCalculationRule(), "Response must include a documented calculation rule");
        assertTrue(response.getCalculationRule().contains("Base Fare"), "Calculation rule must mention Base Fare");
        assertTrue(response.getCalculationRule().contains("Surge"), "Calculation rule must mention Surge multiplier");
        assertTrue(response.getCalculationRule().contains("Service Fee"), "Calculation rule must mention Service Fee");
    }
}
