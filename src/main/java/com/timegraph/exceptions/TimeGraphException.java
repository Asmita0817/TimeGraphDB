package com.timegraph.exceptions;

public class TimeGraphException extends RuntimeException {
    public TimeGraphException(String message) {
        super(message);
    }

    public TimeGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
