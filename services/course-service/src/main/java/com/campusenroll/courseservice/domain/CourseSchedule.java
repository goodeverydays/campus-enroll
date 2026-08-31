package com.campusenroll.courseservice.domain;

public record CourseSchedule(
        long id,
        int dayOfWeek,
        int startSection,
        int endSection,
        String location,
        int startWeek,
        int endWeek) {
}
