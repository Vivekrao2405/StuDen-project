package com.studen.common.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Your session has expired. Please log in again.");
    }
}
