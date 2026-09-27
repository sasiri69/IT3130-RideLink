package com.ridelink.ride.exception;

public class NoAvailableDriverException extends RuntimeException {
    public NoAvailableDriverException(String message) {
        super(message);
    }
}
