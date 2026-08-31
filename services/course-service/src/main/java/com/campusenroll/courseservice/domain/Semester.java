package com.campusenroll.courseservice.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Semester(
        long id,
        String code,
        String name,
        LocalDate startsOn,
        LocalDate endsOn,
        LocalDateTime enrollmentStartsAt,
        LocalDateTime enrollmentEndsAt,
        String status) {
}
