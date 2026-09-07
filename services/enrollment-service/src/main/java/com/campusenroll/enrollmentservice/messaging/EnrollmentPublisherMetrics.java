package com.campusenroll.enrollmentservice.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPublisherMetrics {

    static final String METRIC_NAME = "campus.enrollment.publish";
    private static final String[] OUTCOMES = {
            "confirmed", "nack", "returned", "serialization_error",
            "interrupted", "confirm_failure", "broker_error"
    };

    private final MeterRegistry meterRegistry;

    public EnrollmentPublisherMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (String outcome : OUTCOMES) {
            meterRegistry.counter(METRIC_NAME, "outcome", outcome);
        }
    }

    public void record(String outcome) {
        meterRegistry.counter(METRIC_NAME, "outcome", outcome).increment();
    }
}
