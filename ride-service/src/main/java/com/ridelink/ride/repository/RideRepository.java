package com.ridelink.ride.repository;

import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for Ride entities.
 */
@Repository
public interface RideRepository extends MongoRepository<Ride, String> {

    Optional<Ride> findByRideId(String rideId);

    List<Ride> findByPassengerIdOrderByCreatedAtDesc(String passengerId);

    List<Ride> findByDriverIdOrderByCreatedAtDesc(String driverId);

    List<Ride> findByStatus(RideStatus status);

    boolean existsByRideId(String rideId);

    boolean existsByPassengerIdAndStatusIn(String passengerId, List<RideStatus> statuses);
}
