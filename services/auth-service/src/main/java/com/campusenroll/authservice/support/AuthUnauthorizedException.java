package com.campusenroll.authservice.support;

public class AuthUnauthorizedException extends RuntimeException {

    private final int code;

    public AuthUnauthorizedException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
