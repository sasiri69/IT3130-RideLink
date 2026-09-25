package com.ridelink.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account user representation received from Account Service.
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
