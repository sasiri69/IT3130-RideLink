package com.ridelink.payment.exception;

/**
 * Thrown when receipt for a payment is not found or payment is not completed.
 */
public class ReceiptNotFoundException extends RuntimeException {
    public ReceiptNotFoundException(String message) {
        super(message);
    }
}
