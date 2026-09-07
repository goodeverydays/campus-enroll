package com.campusenroll.enrollmentservice.domain;

import java.time.LocalDateTime;

public record EnrollmentRequestRecord(
        long id,
        String requestId,
        String idempotencyKey,
        long studentId,
        long courseId,
        long offeringId,
        long semesterId,
        String action,
        String status,
        String failureCode,
        String failureMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt) {
}
