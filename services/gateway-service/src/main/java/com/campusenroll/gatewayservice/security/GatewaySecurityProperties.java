package com.campusenroll.gatewayservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.security.jwt")
public record GatewaySecurityProperties(String secretBase64, String issuer, String audience) {
}
