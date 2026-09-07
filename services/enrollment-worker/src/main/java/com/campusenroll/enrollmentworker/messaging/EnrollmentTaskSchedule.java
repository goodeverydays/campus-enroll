package com.campusenroll.enrollmentworker.messaging;

public record EnrollmentTaskSchedule(
        int dayOfWeek,
        int startSection,
        int endSection,
        int startWeek,
        int endWeek) {
}
