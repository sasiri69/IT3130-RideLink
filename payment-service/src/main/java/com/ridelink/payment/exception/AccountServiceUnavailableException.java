package com.ridelink.payment.exception;

/**
 * Thrown when Account Service is unreachable during interservice call.
 */
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
