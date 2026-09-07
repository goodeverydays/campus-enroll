package com.campusenroll.studentservice.domain;

public record StudentProfile(
        long id,
        String studentNo,
        String name,
        long departmentId,
        String departmentName,
        long majorId,
        String majorName,
        int gradeYear,
        String status) {

    public boolean enrollmentEligible() {
        return "ACTIVE".equals(status);
    }
}
