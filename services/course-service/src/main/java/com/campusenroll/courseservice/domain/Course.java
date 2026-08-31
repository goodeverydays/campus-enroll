package com.campusenroll.courseservice.domain;

import java.math.BigDecimal;

public record Course(
        long id,
        String code,
        String name,
        BigDecimal credits,
        int totalHours,
        Long departmentId,
        String status) {
}
