package com.campusenroll.enrollmentservice.support;

public class EnrollmentDependencyException extends RuntimeException {

    public EnrollmentDependencyException(String message) {
        super(message);
    }

    public EnrollmentDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
