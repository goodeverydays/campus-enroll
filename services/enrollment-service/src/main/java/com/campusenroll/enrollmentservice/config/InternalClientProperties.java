package com.campusenroll.enrollmentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.clients")
public record InternalClientProperties(
        String studentBaseUrl,
        String courseBaseUrl) {
}
