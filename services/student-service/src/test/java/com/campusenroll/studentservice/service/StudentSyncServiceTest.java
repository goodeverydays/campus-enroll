package com.campusenroll.studentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.campusenroll.studentservice.domain.LegacyStudentSyncCommand;
import com.campusenroll.studentservice.domain.StudentProfile;
import com.campusenroll.studentservice.repository.StudentRepository;
import com.campusenroll.studentservice.support.ResourceConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentSyncServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentQueryService studentQueryService;

    @Test
    void TestSynchronizeLegacyStudentNewIdentityReturnsCreated() {
        LegacyStudentSyncCommand command = command();
        StudentProfile synchronizedProfile = student(10L, "20260010");
        when(studentRepository.findByLegacyStudentId("legacy-10")).thenReturn(Optional.empty());
        when(studentRepository.findByStudentNo("20260010")).thenReturn(Optional.empty());
        when(studentRepository.synchronize(command, null)).thenReturn(synchronizedProfile);

        var response = studentQueryService.synchronizeLegacyStudent(command);

        assertThat(response.created()).isTrue();
        assertThat(response.student().id()).isEqualTo(10L);
    }

    @Test
    void TestSynchronizeLegacyStudentExistingIdentityReturnsUpdated() {
        LegacyStudentSyncCommand command = command();
        StudentProfile existing = student(10L, "20260010");
        when(studentRepository.findByLegacyStudentId("legacy-10")).thenReturn(Optional.of(existing));
        when(studentRepository.findByStudentNo("20260010")).thenReturn(Optional.of(existing));
        when(studentRepository.synchronize(command, 10L)).thenReturn(existing);

        var response = studentQueryService.synchronizeLegacyStudent(command);

        assertThat(response.created()).isFalse();
        verify(studentRepository).synchronize(command, 10L);
    }

    @Test
    void TestSynchronizeLegacyStudentConflictingIdentitiesReturnsConflict() {
        LegacyStudentSyncCommand command = command();
        when(studentRepository.findByLegacyStudentId("legacy-10"))
                .thenReturn(Optional.of(student(10L, "20260010")));
        when(studentRepository.findByStudentNo("20260010"))
                .thenReturn(Optional.of(student(11L, "20260010")));

        assertThatThrownBy(() -> studentQueryService.synchronizeLegacyStudent(command))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Legacy student ID and student number belong to different students");
        verify(studentRepository, never()).synchronize(command, 10L);
    }

    private static LegacyStudentSyncCommand command() {
        return new LegacyStudentSyncCommand(
                "legacy-10", "20260010", "Test Student", "CS", "Computer Science",
                "SE", "Software Engineering", 2026, "ACTIVE");
    }

    private static StudentProfile student(long id, String studentNo) {
        return new StudentProfile(
                id, studentNo, "Test Student", 2L, "Computer Science", 3L,
                "Software Engineering", 2026, "ACTIVE");
    }
}
