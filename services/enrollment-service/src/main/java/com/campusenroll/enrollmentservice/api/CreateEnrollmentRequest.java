package com.campusenroll.enrollmentservice.api;

import jakarta.validation.constraints.Positive;

public record CreateEnrollmentRequest(@Positive long courseId) {
}
