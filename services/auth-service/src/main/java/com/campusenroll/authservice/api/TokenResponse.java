package com.campusenroll.authservice.api;

public record TokenResponse(String tokenType, String accessToken, long expiresIn) {
}
