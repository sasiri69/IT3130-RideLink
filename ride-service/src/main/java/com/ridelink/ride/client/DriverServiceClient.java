package com.ridelink.ride.client;

import com.ridelink.ride.dto.AvailableDriverResponse;
import com.ridelink.ride.dto.DriverAvailabilityUpdateRequest;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.model.VehicleType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous REST client for communicating with Driver & Vehicle Service (Member 2).
 */
@Component
@Slf4j
public class DriverServiceClient {

    private final RestTemplate restTemplate;
    private final String driverServiceUrl;
    private final String jwtSecret;

    public DriverServiceClient(
            @Value("${services.driver.url:http://localhost:8082}") String driverServiceUrl,
            @Value("${services.driver.timeout-ms:5000}") int timeoutMs,
            @Value("${jwt.secret}") String jwtSecret
    ) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restTemplate = new RestTemplate(factory);
        this.driverServiceUrl = driverServiceUrl.replaceAll("/+$", "");
        this.jwtSecret = jwtSecret;
    }

    /**
     * Queries Driver Service to retrieve available drivers matching area and vehicle type.
     *
     * @param serviceArea Operating area name (e.g. Colombo, Malabe)
     * @param vehicleType Desired vehicle type
     * @return List of eligible available driver representations
     */
    public List<AvailableDriverResponse> getAvailableDrivers(String serviceArea, VehicleType vehicleType) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(driverServiceUrl + "/api/drivers/available");

        if (serviceArea != null && !serviceArea.isBlank()) {
            builder.queryParam("serviceArea", serviceArea.trim());
        }
        if (vehicleType != null) {
            builder.queryParam("vehicleType", vehicleType.name());
        }

        String url = builder.toUriString();
        log.info("Interservice REST call -> Driver Service: GET {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + generateInternalServiceToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<AvailableDriverResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (ResourceAccessException ex) {
            log.warn("Driver Service unreachable at {}: {}", url, ex.getMessage());
            throw new DriverServiceUnavailableException("Driver Service is unreachable at " + driverServiceUrl, ex);
        } catch (Exception ex) {
            log.error("Failed to query available drivers: {}", ex.getMessage());
            throw new DriverServiceUnavailableException("Error querying Driver Service: " + ex.getMessage(), ex);
        }
    }

    /**
     * Updates driver availability status (e.g. BUSY during ride, AVAILABLE after completion).
     *
     * @param driverId Target driver ID
     * @param newStatus New availability status (e.g. BUSY, AVAILABLE)
     * @param authToken Optional user auth token
     */
    public void updateDriverAvailability(String driverId, String newStatus, String authToken) {
        String url = driverServiceUrl + "/api/drivers/" + driverId + "/availability";
        log.info("Interservice REST call -> Driver Service: PATCH {} (status: {})", url, newStatus);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = (authToken != null && !authToken.isBlank()) ? authToken : "Bearer " + generateInternalServiceToken();
        headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);

        DriverAvailabilityUpdateRequest body = new DriverAvailabilityUpdateRequest(newStatus);
        HttpEntity<DriverAvailabilityUpdateRequest> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PATCH, entity, Void.class);
        } catch (Exception ex) {
            log.warn("Could not update availability for driver {}: {}", driverId, ex.getMessage());
            // Do not fail the whole transaction if driver notification encounters non-critical issue
        }
    }

    /**
     * Updates driver statistics (totalRides and rating) after a ride is completed.
     * <p>
     * Calls {@code PATCH /api/drivers/{driverId}/stats} on the Driver Service
     * (Lecture 08 – inter-service communication, Assignment §6.2).
     * </p>
     *
     * @param driverId Target driver ID
     * @param ratingGiven Rating given by passenger (nullable)
     */
    public void updateDriverStats(String driverId, Double ratingGiven) {
        String url = driverServiceUrl + "/api/drivers/" + driverId + "/stats";
        log.info("Interservice REST call -> Driver Service: PATCH {} (rating: {})", url, ratingGiven);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("ridesIncrement", 1);
        if (ratingGiven != null) {
            body.put("newRating", ratingGiven);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PATCH, entity, Void.class);
            log.info("Driver {} stats updated successfully after ride completion", driverId);
        } catch (Exception ex) {
            log.warn("Could not update stats for driver {}: {}", driverId, ex.getMessage());
            // Non-critical: stats update failure should not fail the ride completion
        }
    }

    /**
     * Generates internal service JWT token with ADMIN role.
     */
    private String generateInternalServiceToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("userId", "system-internal-ride-service");

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("ride-service-internal@ridelink.local")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
