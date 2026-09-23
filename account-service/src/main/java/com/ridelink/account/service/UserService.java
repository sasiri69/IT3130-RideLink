package com.ridelink.account.service;

import com.ridelink.account.dto.AuthResponse;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.dto.StatusUpdateRequest;
import com.ridelink.account.dto.UpdateProfileRequest;
import com.ridelink.account.exception.DuplicateEmailException;
import com.ridelink.account.exception.InvalidCredentialsException;
import com.ridelink.account.exception.UserNotFoundException;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.model.RefreshToken;
import com.ridelink.account.model.Role;
import com.ridelink.account.model.User;
import com.ridelink.account.repository.RefreshTokenRepository;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // ─── Registration ──────────────────────────────────────────────────────────

    public AuthResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email " + request.getEmail() + " is already registered!");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        String accessToken = jwtService.generateToken(savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId());
        String rawRefreshToken = generateAndSaveRefreshToken(savedUser.getId());

        return buildAuthResponse(savedUser, accessToken, rawRefreshToken);
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Account is suspended. Please contact support.");
        }

        // Revoke existing refresh tokens before issuing a new one
        refreshTokenRepository.deleteByUserId(user.getId());

        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        String rawRefreshToken = generateAndSaveRefreshToken(user.getId());

        return buildAuthResponse(user, accessToken, rawRefreshToken);
    }

    // ─── Token Refresh ─────────────────────────────────────────────────────────

    public AuthResponse refreshAccessToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        if (storedToken.isRevoked()) {
            throw new IllegalStateException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new IllegalStateException("Refresh token has expired. Please login again.");
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Rotate: revoke old, issue new
        refreshTokenRepository.delete(storedToken);
        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        String newRawRefreshToken = generateAndSaveRefreshToken(user.getId());

        return buildAuthResponse(user, newAccessToken, newRawRefreshToken);
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    public void logout(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    // ─── Profile & Account Management ──────────────────────────────────────────

    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }

    public List<User> getAllUsers() {
        return getAllUsers(null, null);
    }

    public List<User> getAllUsers(Role role, AccountStatus status) {
        return userRepository.findAll().stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> status == null || u.getStatus() == status)
                .toList();
    }

    public User updateProfile(String id, UpdateProfileRequest request) {
        User user = getUserById(id);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User updateAccountStatus(String id, StatusUpdateRequest request) {
        User user = getUserById(id);
        user.setStatus(request.getStatus());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with ID: " + id);
        }
        refreshTokenRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    private String generateAndSaveRefreshToken(String userId) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(refreshExpiration))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .role(user.getRole())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}