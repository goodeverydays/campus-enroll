package com.campusenroll.courseservice.api;

import com.campusenroll.courseservice.domain.CourseCapacity;

public record CourseCapacityResponse(
        long courseId,
        long semesterId,
        int capacity,
        int selectedCount,
        int remainingCount) {

    public static CourseCapacityResponse from(CourseCapacity capacity) {
        return new CourseCapacityResponse(
                capacity.courseId(),
                capacity.semesterId(),
                capacity.capacity(),
                capacity.selectedCount(),
                capacity.remainingCount());
    }
}
