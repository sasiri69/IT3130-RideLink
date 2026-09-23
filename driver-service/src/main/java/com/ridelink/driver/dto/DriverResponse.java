package com.ridelink.driver.dto;

import com.ridelink.driver.model.Driver;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.Location;
import com.ridelink.driver.model.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public DTO representation of a Driver operational profile, including hypermedia navigation links.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {

    private String id;
    private String driverId;
    private String licenseNumber;
    private String serviceArea;
    private DriverStatus status;
    private Vehicle vehicle;
    private Location currentLocation;
    private Double rating;
    private Integer totalRides;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();

    public static DriverResponse fromEntity(Driver driver) {
        if (driver == null) {
            return null;
        }

        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/drivers/" + driver.getDriverId());
        links.put("availability", "/api/drivers/" + driver.getDriverId() + "/availability");
        links.put("location", "/api/drivers/" + driver.getDriverId() + "/location");
        links.put("vehicle", "/api/drivers/" + driver.getDriverId() + "/vehicle");
        links.put("serviceArea", "/api/drivers/" + driver.getDriverId() + "/service-area");

        return DriverResponse.builder()
                .id(driver.getId())
                .driverId(driver.getDriverId())
                .licenseNumber(driver.getLicenseNumber())
                .serviceArea(driver.getServiceArea())
                .status(driver.getStatus())
                .vehicle(driver.getVehicle())
                .currentLocation(driver.getCurrentLocation())
                .rating(driver.getRating())
                .totalRides(driver.getTotalRides())
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .links(links)
                .build();
    }
}
