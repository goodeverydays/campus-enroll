package com.campusenroll.enrollmentservice.api;

import java.time.LocalDateTime;

import com.campusenroll.enrollmentservice.domain.EnrollmentRequestRecord;

public record EnrollmentRequestResponse(
        String requestId,
        long courseId,
        long offeringId,
        long semesterId,
        String action,
        String status,
        String failureCode,
        String failureMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt) {

    public static EnrollmentRequestResponse from(EnrollmentRequestRecord record) {
        return new EnrollmentRequestResponse(
                record.requestId(), record.courseId(), record.offeringId(), record.semesterId(),
                record.action(), record.status(), record.failureCode(), record.failureMessage(),
                record.requestedAt(), record.completedAt());
    }
}
