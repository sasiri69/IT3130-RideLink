package com.ridelink.driver.exception;

public class DriverAlreadyRegisteredException extends RuntimeException {
    public DriverAlreadyRegisteredException(String message) {
        super(message);
    }
}
