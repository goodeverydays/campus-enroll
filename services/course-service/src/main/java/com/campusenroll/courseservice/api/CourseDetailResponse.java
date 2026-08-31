package com.campusenroll.courseservice.api;

import java.math.BigDecimal;

import com.campusenroll.courseservice.domain.Course;

public record CourseDetailResponse(
        long id,
        String code,
        String name,
        BigDecimal credits,
        int totalHours,
        Long departmentId,
        String status) {

    public static CourseDetailResponse from(Course course) {
        return new CourseDetailResponse(
                course.id(),
                course.code(),
                course.name(),
                course.credits(),
                course.totalHours(),
                course.departmentId(),
                course.status());
    }
}
