package com.ridelink.ride.exception;

public class UnauthorizedRideAccessException extends RuntimeException {
    public UnauthorizedRideAccessException(String message) {
        super(message);
    }
}
