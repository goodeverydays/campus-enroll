package com.campusenroll.enrollmentworker.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentWorkerMetrics {

    static final String METRIC_NAME = "campus.enrollment.worker.messages";
    private static final String[] OUTCOMES = {
            "processed", "retry", "dead_letter", "invalid_json", "requeue"
    };

    private final MeterRegistry meterRegistry;

    public EnrollmentWorkerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (String outcome : OUTCOMES) {
            meterRegistry.counter(METRIC_NAME, "outcome", outcome);
        }
    }

    public void record(String outcome) {
        meterRegistry.counter(METRIC_NAME, "outcome", outcome).increment();
    }
}
