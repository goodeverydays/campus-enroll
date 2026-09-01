package com.campusenroll.enrollmentworker.support;

public class WorkerDependencyException extends RuntimeException {

    public WorkerDependencyException(String message) {
        super(message);
    }

    public WorkerDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
