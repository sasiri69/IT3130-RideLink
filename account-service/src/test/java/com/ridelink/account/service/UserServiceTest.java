package com.ridelink.account.service;

import com.ridelink.account.dto.*;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "refreshExpiration", 604800000L);

        sampleUser = User.builder()
                .id("user-001")
                .firstName("Kamal")
                .lastName("Perera")
                .email("kamal@gmail.com")
                .passwordHash("hashed-password")
                .phone("0712345678")
                .role(Role.PASSENGER)
                .status(AccountStatus.ACTIVE)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("Kamal");
        registerRequest.setLastName("Perera");
        registerRequest.setEmail("kamal@gmail.com");
        registerRequest.setPassword("password123");
        registerRequest.setPhone("0712345678");
        registerRequest.setRole(Role.PASSENGER);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("kamal@gmail.com");
        loginRequest.setPassword("password123");
    }

    // ──────────────── REGISTER ────────────────────────────────────────────────

    @Test
    @DisplayName("Register: success — new user saved, JWT + refresh token returned")
    void register_Success() {
        when(userRepository.existsByEmail("kamal@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(anyString(), anyString(), anyString())).thenReturn("jwt-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        AuthResponse response = userService.registerUser(registerRequest);

        assertNotNull(response);
        assertEquals("jwt-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertNotEquals("temp-refresh-token", response.getRefreshToken()); // stub must be gone
        assertEquals("kamal@gmail.com", response.getEmail());
        assertEquals(Role.PASSENGER, response.getRole());

        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Register: duplicate email — throws DuplicateEmailException")
    void register_DuplicateEmail() {
        when(userRepository.existsByEmail("kamal@gmail.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.registerUser(registerRequest));

        verify(userRepository, never()).save(any());
    }

    // ──────────────── LOGIN ───────────────────────────────────────────────────

    @Test
    @DisplayName("Login: success — valid credentials return tokens")
    void login_Success() {
        when(userRepository.findByEmail("kamal@gmail.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString(), anyString())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        AuthResponse response = userService.loginUser(loginRequest);

        assertNotNull(response);
        assertEquals("jwt-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());

        verify(refreshTokenRepository).deleteByUserId("user-001");
    }

    @Test
    @DisplayName("Login: user not found — throws InvalidCredentialsException")
    void login_UserNotFound() {
        when(userRepository.findByEmail("kamal@gmail.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.loginUser(loginRequest));
    }

    @Test
    @DisplayName("Login: wrong password — throws InvalidCredentialsException")
    void login_WrongPassword() {
        when(userRepository.findByEmail("kamal@gmail.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.loginUser(loginRequest));
    }

    @Test
    @DisplayName("Login: suspended account — throws IllegalStateException")
    void login_SuspendedAccount() {
        sampleUser.setStatus(AccountStatus.SUSPENDED);
        when(userRepository.findByEmail("kamal@gmail.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> userService.loginUser(loginRequest));
    }

    // ──────────────── REFRESH TOKEN ───────────────────────────────────────────

    @Test
    @DisplayName("Refresh: expired token — throws IllegalStateException")
    void refresh_ExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .userId("user-001")
                .tokenHash("some-hash")
                .expiresAt(Instant.now().minusSeconds(1000))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThrows(IllegalStateException.class, () -> userService.refreshAccessToken("some-raw-token"));
    }

    @Test
    @DisplayName("Refresh: invalid token — throws InvalidCredentialsException")
    void refresh_InvalidToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.refreshAccessToken("bad-token"));
    }

    // ──────────────── GET USER ────────────────────────────────────────────────

    @Test
    @DisplayName("GetById: success — returns correct user")
    void getUserById_Success() {
        when(userRepository.findById("user-001")).thenReturn(Optional.of(sampleUser));

        User found = userService.getUserById("user-001");

        assertNotNull(found);
        assertEquals("kamal@gmail.com", found.getEmail());
        verify(userRepository, times(1)).findById("user-001");
    }

    @Test
    @DisplayName("GetById: invalid ID — throws UserNotFoundException")
    void getUserById_NotFound() {
        when(userRepository.findById("invalid_id")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserById("invalid_id"));
    }

    @Test
    @DisplayName("GetAllUsers: returns full list")
    void getAllUsers_ReturnsList() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<User> users = userService.getAllUsers();

        assertEquals(1, users.size());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("GetAllUsers: filter by role and status")
    void getAllUsers_WithFilter_ReturnsFilteredList() {
        User driver = User.builder().id("driver-1").role(Role.DRIVER).status(AccountStatus.ACTIVE).build();
        User passenger = User.builder().id("pass-1").role(Role.PASSENGER).status(AccountStatus.ACTIVE).build();
        when(userRepository.findAll()).thenReturn(List.of(driver, passenger));

        List<User> driversOnly = userService.getAllUsers(Role.DRIVER, AccountStatus.ACTIVE);

        assertEquals(1, driversOnly.size());
        assertEquals("driver-1", driversOnly.get(0).getId());
    }

    // ──────────────── UPDATE PROFILE ─────────────────────────────────────────

    @Test
    @DisplayName("UpdateProfile: success — user fields updated")
    void updateProfile_Success() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Nimal");
        req.setLastName("Silva");
        req.setPhone("0771234567");

        when(userRepository.findById("user-001")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateProfile("user-001", req);

        assertEquals("Nimal", updated.getFirstName());
        assertEquals("Silva", updated.getLastName());
        assertEquals("0771234567", updated.getPhone());
    }

    // ──────────────── UPDATE STATUS ──────────────────────────────────────────

    @Test
    @DisplayName("UpdateStatus: ACTIVE → SUSPENDED")
    void updateStatus_Suspend() {
        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setStatus(AccountStatus.SUSPENDED);

        when(userRepository.findById("user-001")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateAccountStatus("user-001", req);

        assertEquals(AccountStatus.SUSPENDED, updated.getStatus());
    }

    // ──────────────── LOGOUT ─────────────────────────────────────────────────

    @Test
    @DisplayName("Logout: deletes all refresh tokens for user")
    void logout_DeletesRefreshTokens() {
        userService.logout("user-001");

        verify(refreshTokenRepository).deleteByUserId("user-001");
    }

    // ──────────────── DELETE USER ─────────────────────────────────────────────

    @Test
    @DisplayName("DeleteUser: success — user and refresh tokens deleted")
    void deleteUser_Success() {
        when(userRepository.existsById("user-001")).thenReturn(true);

        userService.deleteUser("user-001");

        verify(refreshTokenRepository).deleteByUserId("user-001");
        verify(userRepository).deleteById("user-001");
    }

    @Test
    @DisplayName("DeleteUser: not found — throws UserNotFoundException")
    void deleteUser_NotFound() {
        when(userRepository.existsById("invalid_id")).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> userService.deleteUser("invalid_id"));

        verify(refreshTokenRepository, never()).deleteByUserId(anyString());
        verify(userRepository, never()).deleteById(anyString());
    }
}