package com.ridelink.driver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Driver MongoDB entity representing a driver operational profile.
 */
@Document(collection = "drivers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    private String id;

    /**
     * Foreign key identifier referencing the user account in Account Service.
     */
    @Indexed(unique = true)
    private String driverId;

    @Indexed(unique = true)
    private String licenseNumber;

    private String serviceArea;

    @Builder.Default
    private DriverStatus status = DriverStatus.OFFLINE;

    private Vehicle vehicle;

    private Location currentLocation;

    @Builder.Default
    private Double rating = 5.0;

    @Builder.Default
    private Integer totalRides = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
