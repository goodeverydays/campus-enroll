package com.campusenroll.studentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.campusenroll.studentservice.domain.StudentProfile;
import com.campusenroll.studentservice.repository.StudentRepository;
import com.campusenroll.studentservice.support.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentQueryServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentQueryService studentQueryService;

    @Test
    void TestCheckEnrollmentEligibilityActiveStudentReturnsEligible() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student("ACTIVE")));

        var response = studentQueryService.checkEnrollmentEligibility(1L);

        assertThat(response.eligible()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("ELIGIBLE");
    }

    @Test
    void TestCheckEnrollmentEligibilitySuspendedStudentReturnsIneligibleReason() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student("SUSPENDED")));

        var response = studentQueryService.checkEnrollmentEligibility(1L);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("STUDENT_STATUS_SUSPENDED");
    }

    @Test
    void TestFindStudentMissingThrowsResourceNotFound() {
        when(studentRepository.findById(88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentQueryService.findStudent(88L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Student not found: 88");
    }

    private static StudentProfile student(String status) {
        return new StudentProfile(1L, "20260001", "Test Student", 2L, "Computer Science", 3L,
                "Software Engineering", 2026, status);
    }
}
