package com.campusenroll.enrollmentservice.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.messaging")
public record EnrollmentMessagingProperties(
        String exchange,
        String queue,
        String routingKey) {
}
