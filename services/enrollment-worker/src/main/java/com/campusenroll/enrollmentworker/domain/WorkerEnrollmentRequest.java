package com.campusenroll.enrollmentworker.domain;

public record WorkerEnrollmentRequest(
        long id,
        String requestId,
        long studentId,
        long courseId,
        long offeringId,
        long semesterId,
        String action,
        String status) {
}
