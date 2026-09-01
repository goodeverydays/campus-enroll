package com.campusenroll.enrollmentservice.api;

import java.time.LocalDateTime;

import com.campusenroll.enrollmentservice.domain.EnrollmentRecord;

public record EnrollmentResponse(
        long id,
        long courseId,
        long offeringId,
        long semesterId,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime droppedAt) {

    public static EnrollmentResponse from(EnrollmentRecord record) {
        return new EnrollmentResponse(
                record.id(), record.courseId(), record.offeringId(), record.semesterId(),
                record.status(), record.enrolledAt(), record.droppedAt());
    }
}
