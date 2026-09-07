package com.campusenroll.enrollmentworker.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.messaging")
public record EnrollmentMessagingProperties(
        String exchange,
        String queue,
        String routingKey,
        String retryExchange,
        String retryQueue,
        String retryRoutingKey,
        String deadLetterExchange,
        String deadLetterQueue,
        String deadLetterRoutingKey,
        Duration retryDelay,
        int maxAttempts,
        Duration confirmTimeout) {
}
