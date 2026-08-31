package com.campusenroll.courseservice.domain;

public record Teacher(
        long id,
        String teacherNo,
        String name,
        Long departmentId) {
}
