package com.ridelink.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document representing a ride payment record.
 */
@Document(collection = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String paymentId;

    @Indexed
    private String rideId;

    @Indexed
    private String passengerId;

    private String driverId;

    private Double amount;

    private Double baseFare;

    private Double distanceFare;

    private Double serviceFee;

    private Double discount;

    @Builder.Default
    private String currency = "LKR";

    private PaymentMethod paymentMethod;

    private PaymentStatus status;

    private String transactionReference;

    private String failureReason;

    private String refundReason;

    private LocalDateTime paidAt;

    private LocalDateTime refundedAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}
