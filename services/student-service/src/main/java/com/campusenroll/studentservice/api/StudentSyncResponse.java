package com.campusenroll.studentservice.api;

import com.campusenroll.studentservice.domain.StudentProfile;

public record StudentSyncResponse(
        boolean created,
        StudentProfileResponse student) {

    public static StudentSyncResponse from(StudentProfile profile, boolean created) {
        return new StudentSyncResponse(created, StudentProfileResponse.from(profile));
    }
}
