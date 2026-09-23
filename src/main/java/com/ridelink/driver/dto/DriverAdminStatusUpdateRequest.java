package com.ridelink.driver.dto;

import com.ridelink.driver.model.DriverStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAdminStatusUpdateRequest {

    @NotNull(message = "Target status is required (e.g. OFFLINE, AVAILABLE, BUSY, SUSPENDED)")
    private DriverStatus status;
}
