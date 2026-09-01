package com.campusenroll.enrollmentservice.domain;

import java.time.LocalDateTime;

public record EnrollmentRecord(
        long id,
        long studentId,
        long courseId,
        long offeringId,
        long semesterId,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime droppedAt) {
}
