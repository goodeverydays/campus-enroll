package com.campusenroll.studentservice.domain;

public record LegacyStudentSyncCommand(
        String legacyStudentId,
        String studentNo,
        String name,
        String departmentCode,
        String departmentName,
        String majorCode,
        String majorName,
        int gradeYear,
        String status) {
}
