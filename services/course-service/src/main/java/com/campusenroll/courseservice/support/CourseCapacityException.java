package com.campusenroll.courseservice.support;

public class CourseCapacityException extends RuntimeException {

    private final int code;

    public CourseCapacityException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
