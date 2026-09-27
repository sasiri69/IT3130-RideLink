package com.ridelink.account.controller;

import com.ridelink.account.dto.StatusUpdateRequest;
import com.ridelink.account.dto.UpdateProfileRequest;
import com.ridelink.account.dto.UserResponse;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.model.Role;
import com.ridelink.account.security.JwtService;
import com.ridelink.account.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for managing user profiles and account state")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    // ─── Own profile (any authenticated user) ──────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the currently logged-in user's profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserResponse> getMyProfile(@RequestHeader("Authorization") String authHeader) {
        String jwt = authHeader.substring(7);
        String userId = jwtService.extractClaim(jwt, claims -> claims.get("userId", String.class));
        return ResponseEntity.ok(UserResponse.fromEntity(userService.getUserById(userId)));
    }

    // ─── Admin-only ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get all users with optional filtering by role and status (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of users returned"),
        @ApiResponse(responseCode = "403", description = "Access denied — Admin role required")
    })
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status) {
        List<UserResponse> users = userService.getAllUsers(role, status).stream()
                .map(UserResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(UserResponse.fromEntity(userService.getUserById(id)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update account status — ACTIVE / INACTIVE / SUSPENDED (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(UserResponse.fromEntity(userService.updateAccountStatus(id, request)));
    }

    // ─── Own profile update (PATCH per REST standards; PUT also accepted) ──────

    @PatchMapping("/me/profile")
    @Operation(summary = "Partially update the currently logged-in user's profile details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserResponse> updateMyProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UpdateProfileRequest request) {
        String jwt = authHeader.substring(7);
        String userId = jwtService.extractClaim(jwt, claims -> claims.get("userId", String.class));
        return ResponseEntity.ok(UserResponse.fromEntity(userService.updateProfile(userId, request)));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Update profile details (PUT alias for PATCH /me/profile)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserResponse> updateMyProfilePut(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UpdateProfileRequest request) {
        return updateMyProfile(authHeader, request);
    }

    // ─── Account Deletion ───────────────────────────────────────────────────────

    @DeleteMapping("/me")
    @Operation(summary = "Delete the currently logged-in user's account and revoke all sessions")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted successfully (no content)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing token"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> deleteMyAccount(@RequestHeader("Authorization") String authHeader) {
        String jwt = authHeader.substring(7);
        String userId = jwtService.extractClaim(jwt, claims -> claims.get("userId", String.class));
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user by ID (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted successfully (no content)"),
        @ApiResponse(responseCode = "403", description = "Access denied — Admin role required"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Void> deleteUserById(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}