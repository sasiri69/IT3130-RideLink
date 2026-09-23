package com.ridelink.driver.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Interservice DTO representing the user contract received from Member 1 (Account Service).
 * Avoids any direct cross-database dependencies (Lecture 07/08 & Section 6.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUserResponse {

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
