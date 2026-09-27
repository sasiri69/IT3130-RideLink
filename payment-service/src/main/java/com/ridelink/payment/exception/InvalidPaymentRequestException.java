package com.ridelink.payment.exception;

/**
 * Thrown when payment input or state fails business validation.
 */
public class InvalidPaymentRequestException extends RuntimeException {
    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
