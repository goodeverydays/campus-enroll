package com.campusenroll.studentservice.service;

import com.campusenroll.studentservice.api.EnrollmentEligibilityResponse;
import com.campusenroll.studentservice.api.StudentProfileResponse;
import com.campusenroll.studentservice.domain.StudentProfile;
import com.campusenroll.studentservice.repository.StudentRepository;
import com.campusenroll.studentservice.support.ResourceNotFoundException;
import org.springframework.stereotype.Service;

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

    private StudentProfile requireStudent(long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
    }
}
