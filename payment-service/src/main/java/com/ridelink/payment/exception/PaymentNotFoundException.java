package com.ridelink.payment.exception;

/**
 * Thrown when a requested payment cannot be found.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
