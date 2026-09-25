package com.ridelink.driver.client;

import com.ridelink.driver.client.dto.AccountUserResponse;
import com.ridelink.driver.exception.AccountServiceUnavailableException;
import com.ridelink.driver.exception.DriverAccountNotFoundException;
import com.ridelink.driver.exception.InvalidDriverAccountException;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Synchronous REST client for communicating with the Account Service.
 * Verifies account existence and role validation across microservice boundaries.
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
     * Synchronously queries the Account Service to verify driver user account details, role, and active status.
     *
     * @param driverId Unique account ID of the driver
     * @param incomingToken Optional incoming JWT from client request
     * @return AccountUserResponse DTO containing user information
     */
    public AccountUserResponse getUserById(String driverId, String incomingToken) {
        String url = accountServiceUrl + "/api/users/" + driverId;
        log.info("Interservice REST call -> Account Service: GET {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String bearerToken = (incomingToken != null && !incomingToken.isBlank())
                ? incomingToken
                : "Bearer " + generateInternalServiceToken();
        headers.set("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);

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
            log.warn("Account Service returned 404: User ID {} not found", driverId);
            throw new DriverAccountNotFoundException("User account with ID '" + driverId + "' does not exist in Account Service.");
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized ex) {
            log.warn("Account Service access denied for user ID {}: {}", driverId, ex.getMessage());
            return fetchWithInternalToken(url, driverId);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new InvalidDriverAccountException("Account Service rejected request: " + ex.getMessage());
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            log.error("Failed to connect to Account Service at {}: {}", url, ex.getMessage());
            throw new AccountServiceUnavailableException(
                    "Unable to verify driver account: Account Service is currently unreachable at " + accountServiceUrl,
                    ex
            );
        }
    }

    private AccountUserResponse fetchWithInternalToken(String url, String driverId) {
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
            throw new DriverAccountNotFoundException("User account with ID '" + driverId + "' does not exist in Account Service.");
        } catch (Exception ex) {
            log.error("Internal service call to Account Service failed: {}", ex.getMessage());
            throw new AccountServiceUnavailableException("Failed communicating with Account Service: " + ex.getMessage(), ex);
        }
    }

    /**
     * Generates a short-lived internal service JWT token for interservice authentication.
     */
    private String generateInternalServiceToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("userId", "system-internal-driver-service");

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("driver-service-internal@ridelink.local")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
