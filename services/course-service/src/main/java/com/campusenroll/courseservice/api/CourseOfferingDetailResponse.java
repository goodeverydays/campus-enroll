package com.campusenroll.courseservice.api;

import java.util.List;

import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.domain.CourseSchedule;

public record CourseOfferingDetailResponse(
        CourseOfferingResponse offering,
        List<CourseScheduleResponse> schedules) {

    public CourseOfferingDetailResponse {
        schedules = List.copyOf(schedules);
    }

    public static CourseOfferingDetailResponse from(
            CourseOffering offering,
            List<CourseSchedule> schedules) {
        return new CourseOfferingDetailResponse(
                CourseOfferingResponse.from(offering),
                schedules.stream().map(CourseScheduleResponse::from).toList());
    }
}
