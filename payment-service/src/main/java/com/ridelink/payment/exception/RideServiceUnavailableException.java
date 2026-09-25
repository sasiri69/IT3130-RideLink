package com.ridelink.payment.exception;

/**
 * Thrown when Ride Management Service is unreachable during interservice call.
 */
public class RideServiceUnavailableException extends RuntimeException {
    public RideServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
