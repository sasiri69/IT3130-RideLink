package com.ridelink.driver.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverRegisterRequest {

    /**
     * Account Service User ID (issued by Member 1 upon account registration).
     */
    @NotBlank(message = "Driver ID is required (Account Service User ID)")
    private String driverId;

    @NotBlank(message = "Driver license number is required")
    @Pattern(regexp = "^[A-Z0-9-]{5,15}$", message = "Invalid license number format (e.g. B1234567)")
    private String licenseNumber;

    @NotBlank(message = "Operational service area is required (e.g. Colombo, Kandy, Galle, Malabe)")
    private String serviceArea;

    @NotNull(message = "Vehicle details are mandatory for driver registration")
    @Valid
    private VehicleRequest vehicle;

    @Valid
    private LocationRequest initialLocation;
}
