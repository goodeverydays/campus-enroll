package com.campusenroll.studentservice.api;

public record EnrollmentEligibilityResponse(
        long studentId,
        boolean eligible,
        String reasonCode) {
}
