package com.campusenroll.studentservice.repository;

import java.util.Optional;

import com.campusenroll.studentservice.domain.StudentProfile;

public interface StudentRepository {

    Optional<StudentProfile> findById(long studentId);
}
