package com.ridelink.account.exception;

/**
 * Thrown when user authentication fails due to incorrect email or password.
 * Mapped to HTTP 401 Unauthorized per REST principles (Lecture 07/08).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
