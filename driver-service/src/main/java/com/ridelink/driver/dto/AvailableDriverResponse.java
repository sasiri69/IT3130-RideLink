package com.ridelink.driver.dto;

import com.ridelink.driver.model.Driver;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.Location;
import com.ridelink.driver.model.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableDriverResponse {

    private String driverId;
    private String serviceArea;
    private DriverStatus status;
    private Vehicle vehicle;
    private Location currentLocation;
    private Double rating;
    private Integer totalRides;
    private Double distanceKm;

    public static AvailableDriverResponse fromEntity(Driver driver, Double distanceKm) {
        if (driver == null) {
            return null;
        }
        return AvailableDriverResponse.builder()
                .driverId(driver.getDriverId())
                .serviceArea(driver.getServiceArea())
                .status(driver.getStatus())
                .vehicle(driver.getVehicle())
                .currentLocation(driver.getCurrentLocation())
                .rating(driver.getRating())
                .totalRides(driver.getTotalRides())
                .distanceKm(distanceKm)
                .build();
    }
}
