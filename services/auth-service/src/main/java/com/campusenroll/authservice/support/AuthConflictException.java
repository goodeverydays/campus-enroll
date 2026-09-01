package com.campusenroll.authservice.support;

public class AuthConflictException extends RuntimeException {

    public AuthConflictException(String message) {
        super(message);
    }
}
