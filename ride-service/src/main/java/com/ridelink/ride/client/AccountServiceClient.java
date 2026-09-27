package com.ridelink.ride.client;

import com.ridelink.ride.client.dto.AccountUserResponse;
import com.ridelink.ride.exception.AccountServiceUnavailableException;
import com.ridelink.ride.exception.InvalidPassengerAccountException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Synchronous REST client for communicating with Account Service (Member 1).
 */
@Component
@Slf4j
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final String accountServiceUrl;
    private final String jwtSecret;

    public AccountServiceClient(
            @Value("${services.account.url:http://localhost:8081}") String accountServiceUrl,
            @Value("${services.account.timeout-ms:5000}") int timeoutMs,
            @Value("${jwt.secret}") String jwtSecret
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restTemplate = new RestTemplate(factory);
        this.accountServiceUrl = accountServiceUrl.replaceAll("/+$", "");
        this.jwtSecret = jwtSecret;
    }

    /**
     * Verifies passenger account existence and status with Account Service.
     *
     * @param passengerId User account ID
     * @param incomingToken Optional authorization header from caller
     * @return AccountUserResponse DTO
     */
    public AccountUserResponse getPassengerById(String passengerId, String incomingToken) {
        String url = accountServiceUrl + "/api/users/" + passengerId;
        log.info("Interservice REST call -> Account Service: GET {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String token = (incomingToken != null && !incomingToken.isBlank())
                ? incomingToken
                : "Bearer " + generateInternalServiceToken();
        headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<AccountUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    AccountUserResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Account Service returned 404: Passenger ID {} not found", passengerId);
            throw new InvalidPassengerAccountException("Passenger account with ID '" + passengerId + "' does not exist.");
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized ex) {
            log.warn("Account Service access denied with user token; retrying with internal service token: {}", ex.getMessage());
            return fetchWithInternalToken(url, passengerId);
        } catch (ResourceAccessException ex) {
            log.warn("Account Service unreachable at {}: {}", url, ex.getMessage());
            throw new AccountServiceUnavailableException("Account Service is unreachable at " + accountServiceUrl, ex);
        } catch (Exception ex) {
            log.error("Failed to query passenger account {}: {}", passengerId, ex.getMessage());
            throw new AccountServiceUnavailableException("Error querying Account Service: " + ex.getMessage(), ex);
        }
    }

    private AccountUserResponse fetchWithInternalToken(String url, String passengerId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + generateInternalServiceToken());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<AccountUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    AccountUserResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new InvalidPassengerAccountException("Passenger account with ID '" + passengerId + "' does not exist.");
        } catch (Exception ex) {
            throw new AccountServiceUnavailableException("Failed communicating with Account Service: " + ex.getMessage(), ex);
        }
    }

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
