package com.campusenroll.courseservice.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.campusenroll.courseservice.domain.Semester;

public record SemesterResponse(
        long id,
        String code,
        String name,
        LocalDate startsOn,
        LocalDate endsOn,
        LocalDateTime enrollmentStartsAt,
        LocalDateTime enrollmentEndsAt,
        String status) {

    public static SemesterResponse from(Semester semester) {
        return new SemesterResponse(
                semester.id(), semester.code(), semester.name(), semester.startsOn(), semester.endsOn(),
                semester.enrollmentStartsAt(), semester.enrollmentEndsAt(), semester.status());
    }
}
