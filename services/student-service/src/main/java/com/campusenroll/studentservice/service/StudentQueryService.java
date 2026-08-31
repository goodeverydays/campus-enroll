package com.campusenroll.studentservice.service;

import com.campusenroll.studentservice.api.EnrollmentEligibilityResponse;
import com.campusenroll.studentservice.api.StudentProfileResponse;
import com.campusenroll.studentservice.api.StudentSyncResponse;
import com.campusenroll.studentservice.domain.LegacyStudentSyncCommand;
import com.campusenroll.studentservice.domain.StudentProfile;
import com.campusenroll.studentservice.repository.StudentRepository;
import com.campusenroll.studentservice.support.ResourceNotFoundException;
import com.campusenroll.studentservice.support.ResourceConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

@Service
public class StudentQueryService {

    private final StudentRepository studentRepository;

    public StudentQueryService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public StudentProfileResponse findStudent(long studentId) {
        return StudentProfileResponse.from(requireStudent(studentId));
    }

    public EnrollmentEligibilityResponse checkEnrollmentEligibility(long studentId) {
        StudentProfile profile = requireStudent(studentId);
        return new EnrollmentEligibilityResponse(
                profile.id(),
                profile.enrollmentEligible(),
                profile.enrollmentEligible() ? "ELIGIBLE" : "STUDENT_STATUS_" + profile.status());
    }

    @Transactional
    public StudentSyncResponse synchronizeLegacyStudent(LegacyStudentSyncCommand command) {
        Optional<StudentProfile> byLegacyId = studentRepository.findByLegacyStudentId(command.legacyStudentId());
        Optional<StudentProfile> byStudentNo = studentRepository.findByStudentNo(command.studentNo());
        if (byLegacyId.isPresent()
                && byStudentNo.isPresent()
                && byLegacyId.get().id() != byStudentNo.get().id()) {
            throw new ResourceConflictException("Legacy student ID and student number belong to different students");
        }

        Long existingStudentId = byLegacyId.map(StudentProfile::id)
                .or(() -> byStudentNo.map(StudentProfile::id))
                .orElse(null);
        try {
            StudentProfile profile = studentRepository.synchronize(command, existingStudentId);
            return StudentSyncResponse.from(profile, existingStudentId == null);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("Legacy student identity changed concurrently", exception);
        }
    }

    private StudentProfile requireStudent(long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
    }
}
