package com.campusenroll.enrollmentworker.messaging;

import java.time.Instant;
import java.util.List;

public record EnrollmentTask(
        String requestId,
        long studentId,
        long courseId,
        long offeringId,
        long semesterId,
        List<EnrollmentTaskSchedule> schedules,
        Instant submittedAt) {

    public EnrollmentTask {
        schedules = List.copyOf(schedules);
    }
}
