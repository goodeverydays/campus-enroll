package com.campusenroll.courseservice.domain;

public record CourseCapacity(
        long courseId,
        long semesterId,
        int capacity,
        int selectedCount) {

    public int remainingCount() {
        return Math.max(0, capacity - selectedCount);
    }
}
