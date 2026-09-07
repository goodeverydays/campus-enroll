package com.campusenroll.courseservice.api;

import com.campusenroll.courseservice.domain.CourseOffering;

public record CourseOfferingResponse(
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
        int remainingCount,
        String status) {

    public static CourseOfferingResponse from(CourseOffering offering) {
        return new CourseOfferingResponse(
                offering.id(), offering.courseId(), offering.courseCode(), offering.courseName(),
                offering.semesterId(), offering.semesterName(), offering.teacherId(), offering.teacherName(),
                offering.sectionNo(), offering.capacity(), offering.selectedCount(), offering.remainingCount(),
                offering.status());
    }
}
