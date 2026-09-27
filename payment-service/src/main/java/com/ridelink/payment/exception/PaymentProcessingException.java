package com.ridelink.payment.exception;

/**
 * Thrown when simulated payment gateway processing fails (e.g. card declined).
 */
public class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String message) {
        super(message);
    }
}
