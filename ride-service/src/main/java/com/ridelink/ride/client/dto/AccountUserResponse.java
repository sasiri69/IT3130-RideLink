package com.ridelink.ride.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representation of user details retrieved from Account Service (Member 1).
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
}
