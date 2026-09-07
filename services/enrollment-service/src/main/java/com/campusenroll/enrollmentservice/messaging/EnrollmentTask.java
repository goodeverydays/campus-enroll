package com.campusenroll.enrollmentservice.messaging;

import java.time.Instant;
import java.util.List;

import com.campusenroll.enrollmentservice.client.CourseSchedule;

public record EnrollmentTask(
        String requestId,
        long studentId,
        long courseId,
        long offeringId,
        long semesterId,
        List<CourseSchedule> schedules,
        Instant submittedAt) {

    public EnrollmentTask {
        schedules = List.copyOf(schedules);
    }
}
