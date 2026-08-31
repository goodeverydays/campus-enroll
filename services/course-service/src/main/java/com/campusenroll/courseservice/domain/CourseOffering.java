package com.campusenroll.courseservice.domain;

public record CourseOffering(
        long id,
        long courseId,
        String courseCode,
        String courseName,
        long semesterId,
        String semesterName,
        long teacherId,
        String teacherName,
        String sectionNo,
        int capacity,
        int selectedCount,
        String status) {

    public int remainingCount() {
        return Math.max(0, capacity - selectedCount);
    }
}
