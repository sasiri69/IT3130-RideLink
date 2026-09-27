package com.ridelink.ride.dto;

import com.ridelink.ride.model.LocationPoint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for GPS location points.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPointRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @NotBlank(message = "Address or place name is required")
    private String addressName;

    public LocationPoint toEntity() {
        return LocationPoint.builder()
                .latitude(this.latitude)
                .longitude(this.longitude)
                .addressName(this.addressName != null ? this.addressName.trim() : null)
                .build();
    }
}
