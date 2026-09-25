package com.ridelink.payment.exception;

/**
 * Thrown when an attempt is made to pay for a ride that has already been paid.
 */
public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String message) {
        super(message);
    }
}
