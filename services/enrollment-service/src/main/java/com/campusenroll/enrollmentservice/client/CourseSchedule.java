package com.campusenroll.enrollmentservice.client;

public record CourseSchedule(
        int dayOfWeek,
        int startSection,
        int endSection,
        int startWeek,
        int endWeek) {
}
