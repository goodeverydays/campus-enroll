package com.campusenroll.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.security")
public record SecurityProperties(Jwt jwt, Sso sso) {

    public record Jwt(String secretBase64, String issuer, String audience, long ttlSeconds) {
    }

    public record Sso(long ticketTtlSeconds, String legacySystemApiKey) {
    }
}
