package com.campusenroll.enrollmentworker.support;

public class WorkerBusinessException extends RuntimeException {

    private final int code;

    public WorkerBusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
