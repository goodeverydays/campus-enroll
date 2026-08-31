package com.campusenroll.studentservice.repository;

import java.util.Optional;

import com.campusenroll.studentservice.domain.StudentProfile;
import com.campusenroll.studentservice.domain.LegacyStudentSyncCommand;

public interface StudentRepository {

    Optional<StudentProfile> findById(long studentId);

    Optional<StudentProfile> findByLegacyStudentId(String legacyStudentId);

    Optional<StudentProfile> findByStudentNo(String studentNo);

    StudentProfile synchronize(LegacyStudentSyncCommand command, Long existingStudentId);
}
