package com.ridelink.account.dto;

import com.ridelink.account.model.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    @NotNull(message = "Account status is required")
    private AccountStatus status;
}