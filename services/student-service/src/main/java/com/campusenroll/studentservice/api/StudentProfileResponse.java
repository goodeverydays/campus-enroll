package com.campusenroll.studentservice.api;

import com.campusenroll.studentservice.domain.StudentProfile;

public record StudentProfileResponse(
        long id,
        String studentNo,
        String name,
        long departmentId,
        String departmentName,
        long majorId,
        String majorName,
        int gradeYear,
        String status) {

    public static StudentProfileResponse from(StudentProfile profile) {
        return new StudentProfileResponse(
                profile.id(),
                profile.studentNo(),
                profile.name(),
                profile.departmentId(),
                profile.departmentName(),
                profile.majorId(),
                profile.majorName(),
                profile.gradeYear(),
                profile.status());
    }
}
