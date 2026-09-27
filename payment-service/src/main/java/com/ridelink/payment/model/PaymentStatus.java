package com.ridelink.payment.model;

/**
 * Lifecycle status of a payment transaction.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
