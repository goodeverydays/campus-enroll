package com.campusenroll.enrollmentworker.domain;

public record WorkerEnrollment(
        long id,
        long studentId,
        long courseId,
        long offeringId,
        long semesterId,
        String sourceRequestId,
        String status) {
}
