package com.ridelink.ride.exception;

public class DriverServiceUnavailableException extends RuntimeException {
    public DriverServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
