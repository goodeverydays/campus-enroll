package com.campusenroll.enrollmentworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.clients")
public record WorkerClientProperties(String courseBaseUrl) {
}
