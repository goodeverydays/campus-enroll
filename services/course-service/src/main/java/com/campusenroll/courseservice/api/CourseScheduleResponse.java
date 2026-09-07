package com.campusenroll.courseservice.api;

import com.campusenroll.courseservice.domain.CourseSchedule;

public record CourseScheduleResponse(
        long id,
        int dayOfWeek,
        int startSection,
        int endSection,
        String location,
        int startWeek,
        int endWeek) {

    public static CourseScheduleResponse from(CourseSchedule schedule) {
        return new CourseScheduleResponse(
                schedule.id(), schedule.dayOfWeek(), schedule.startSection(), schedule.endSection(),
                schedule.location(), schedule.startWeek(), schedule.endWeek());
    }
}
