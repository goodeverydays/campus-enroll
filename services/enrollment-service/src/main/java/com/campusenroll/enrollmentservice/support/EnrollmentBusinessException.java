package com.campusenroll.enrollmentservice.support;

public class EnrollmentBusinessException extends RuntimeException {

    private final int code;

    public EnrollmentBusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
