package com.ridelink.payment.repository;

import com.ridelink.payment.model.Payment;
import com.ridelink.payment.model.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for Payment documents.
 */
@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByRideId(String rideId);

    List<Payment> findByPassengerIdOrderByCreatedAtDesc(String passengerId);

    List<Payment> findByDriverIdOrderByCreatedAtDesc(String driverId);

    List<Payment> findByStatus(PaymentStatus status);

    boolean existsByRideIdAndStatus(String rideId, PaymentStatus status);
}
