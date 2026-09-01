package com.campusenroll.enrollmentservice.client;

import java.util.List;

public record EnrollmentCandidate(
        long courseId,
        long offeringId,
        long semesterId,
        List<CourseSchedule> schedules) {

    public EnrollmentCandidate {
        schedules = List.copyOf(schedules);
    }
}
