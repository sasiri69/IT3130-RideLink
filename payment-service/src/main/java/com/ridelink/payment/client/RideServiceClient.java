package com.ridelink.payment.client;

import com.ridelink.payment.client.dto.RideClientResponse;
import com.ridelink.payment.exception.InvalidPaymentRequestException;
import com.ridelink.payment.exception.RideServiceUnavailableException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Synchronous REST client for communicating with Ride Management Service (Member 3).
 */
@Component
@Slf4j
public class RideServiceClient {

    private final RestTemplate restTemplate;
    private final String rideServiceUrl;
    private final String jwtSecret;
    private final boolean bypassOnUnreachable;

    public RideServiceClient(
            @Value("${services.ride.url:http://localhost:8083}") String rideServiceUrl,
            @Value("${services.ride.timeout-ms:5000}") int timeoutMs,
            @Value("${services.ride.bypass-on-unreachable:true}") boolean bypassOnUnreachable,
            @Value("${jwt.secret}") String jwtSecret
    ) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restTemplate = new RestTemplate(factory);
        this.rideServiceUrl = rideServiceUrl.replaceAll("/+$", "");
        this.jwtSecret = jwtSecret;
        this.bypassOnUnreachable = bypassOnUnreachable;
    }

    /**
     * Retrieves ride booking details from Ride Management Service.
     *
     * @param rideId Public ride ID (e.g. RIDE-XXXX)
     * @param incomingToken Optional caller authorization token
     * @return RideClientResponse DTO
     */
    public RideClientResponse getRideById(String rideId, String incomingToken) {
        String url = rideServiceUrl + "/api/rides/" + rideId;
        log.info("Interservice REST call -> Ride Service: GET {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String token = (incomingToken != null && !incomingToken.isBlank())
                ? incomingToken
                : "Bearer " + generateInternalServiceToken();
        headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RideClientResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RideClientResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Ride Service returned 404: Ride ID {} not found", rideId);
            throw new InvalidPaymentRequestException("Ride with ID '" + rideId + "' does not exist.");
        } catch (ResourceAccessException ex) {
            log.warn("Ride Service unreachable at {}: {}", url, ex.getMessage());
            if (bypassOnUnreachable) {
                log.info("Bypass active: creating simulated ride record for '{}'", rideId);
                return RideClientResponse.builder()
                        .rideId(rideId)
                        .passengerId("simulated-passenger")
                        .driverId("simulated-driver")
                        .status("COMPLETED")
                        .distanceKm(10.0)
                        .estimatedFare(1500.0)
                        .finalFare(1500.0)
                        .build();
            }
            throw new RideServiceUnavailableException("Ride Service is unreachable at " + rideServiceUrl, ex);
        } catch (Exception ex) {
            log.error("Failed to query ride {}: {}", rideId, ex.getMessage());
            throw new RideServiceUnavailableException("Error querying Ride Service: " + ex.getMessage(), ex);
        }
    }

    private String generateInternalServiceToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("userId", "system-internal-payment-service");

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("payment-service-internal@ridelink.local")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
