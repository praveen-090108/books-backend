package com.intelliatech.app.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Too many requests. Please wait and try again.");
    }
}
