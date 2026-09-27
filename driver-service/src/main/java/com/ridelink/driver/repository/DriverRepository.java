package com.ridelink.driver.repository;

import com.ridelink.driver.model.Driver;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.VehicleType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends MongoRepository<Driver, String> {

    Optional<Driver> findByDriverId(String driverId);

    boolean existsByDriverId(String driverId);

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByVehicleLicensePlate(String licensePlate);

    List<Driver> findByStatus(DriverStatus status);

    List<Driver> findByStatusAndServiceAreaIgnoreCase(DriverStatus status, String serviceArea);

    List<Driver> findByStatusAndServiceAreaIgnoreCaseAndVehicleVehicleType(
            DriverStatus status,
            String serviceArea,
            VehicleType vehicleType
    );

    List<Driver> findByServiceAreaIgnoreCase(String serviceArea);
}
