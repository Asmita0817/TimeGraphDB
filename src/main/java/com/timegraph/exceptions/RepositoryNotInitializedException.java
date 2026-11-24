package com.timegraph.exceptions;

public class RepositoryNotInitializedException extends TimeGraphException {
    public RepositoryNotInitializedException(String message) {
        super(message);
    }

    public RepositoryNotInitializedException() {
        super("Repository is not initialized. Run 'INIT' command first.");
    }
}
