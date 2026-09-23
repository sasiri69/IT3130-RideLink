package com.ridelink.driver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAreaUpdateRequest {

    @NotBlank(message = "Service area is required (e.g. Colombo, Kandy, Galle, Malabe)")
    private String serviceArea;
}
