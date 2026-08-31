package com.campusenroll.courseservice.api;

import java.math.BigDecimal;

import com.campusenroll.courseservice.domain.Course;

public record CourseSummaryResponse(
        long id,
        String code,
        String name,
        BigDecimal credits,
        int totalHours,
        String status) {

    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.id(),
                course.code(),
                course.name(),
                course.credits(),
                course.totalHours(),
                course.status());
    }
}
