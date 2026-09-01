package com.campusenroll.enrollmentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.campusenroll.enrollmentservice.client.AcademicClient;
import com.campusenroll.enrollmentservice.client.CourseSchedule;
import com.campusenroll.enrollmentservice.client.EnrollmentCandidate;
import com.campusenroll.enrollmentservice.client.StudentEligibilityClient;
import com.campusenroll.enrollmentservice.domain.EnrollmentRecord;
import com.campusenroll.enrollmentservice.domain.EnrollmentRequestRecord;
import com.campusenroll.enrollmentservice.messaging.EnrollmentTask;
import com.campusenroll.enrollmentservice.messaging.RabbitEnrollmentPublisher;
import com.campusenroll.enrollmentservice.repository.EnrollmentRepository;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class EnrollmentApplicationServiceTest {

    @Mock private EnrollmentRepository repository;
    @Mock private StudentEligibilityClient studentClient;
    @Mock private AcademicClient academicClient;
    @Mock private RedisReservationService redisReservationService;
    @Mock private RabbitEnrollmentPublisher enrollmentPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    private EnrollmentApplicationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        service = new EnrollmentApplicationService(
                repository, studentClient, academicClient, redisReservationService,
                enrollmentPublisher, transactionTemplate);
    }

    @Test
    void TestEnrollEligibleStudentPublishesPendingTask() {
        EnrollmentCandidate candidate = candidate();
        EnrollmentRequestRecord pending = request("ENROLL", "PENDING", null, null);
        prepareNewEnrollment(candidate, pending);

        var response = service.enroll(1L, "key-1", 20L);

        assertThat(response.status()).isEqualTo("PENDING");
        verify(studentClient).requireEligible(1L);
        verify(redisReservationService).reserve(candidate, 1L, "request-1");
        ArgumentCaptor<EnrollmentTask> taskCaptor = ArgumentCaptor.forClass(EnrollmentTask.class);
        verify(enrollmentPublisher).publish(taskCaptor.capture());
        assertThat(taskCaptor.getValue().requestId()).isEqualTo("request-1");
        assertThat(taskCaptor.getValue().schedules()).isEqualTo(candidate.schedules());
        verify(academicClient, never()).reserve(anyLong());
        verify(repository, never()).createEnrollment(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(repository, never()).markRequestSuccess(anyLong());
    }

    @Test
    void TestDuplicateEnrollmentPersistsFailureWithoutPublishing() {
        EnrollmentRequestRecord pending = request("ENROLL", "PENDING", null, null);
        EnrollmentRequestRecord failed = request("ENROLL", "FAILED", "40910", "Course is already enrolled");
        when(repository.findRequestByIdempotency(1L, "key-1")).thenReturn(Optional.empty());
        when(repository.createRequest(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(pending);
        when(repository.lockRequest(100L)).thenReturn(Optional.of(pending));
        when(repository.findActiveEnrollment(1L, 20L)).thenReturn(Optional.of(enrollment("ENROLLED")));
        when(repository.findRequestByRequestId("request-1")).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.enroll(1L, "key-1", 20L))
                .isInstanceOfSatisfying(EnrollmentBusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40910));

        verify(repository).markRequestFailed(100L, 40910, "Course is already enrolled");
        verify(redisReservationService, never()).reserve(any(), anyLong(), anyString());
        verify(enrollmentPublisher, never()).publish(any());
    }

    @Test
    void TestScheduleConflictPersistsFailureWithoutPublishing() {
        EnrollmentCandidate candidate = candidate();
        EnrollmentRequestRecord pending = request("ENROLL", "PENDING", null, null);
        EnrollmentRequestRecord failed = request(
                "ENROLL", "FAILED", "40913", "Course schedule conflicts with an existing enrollment");
        when(repository.findRequestByIdempotency(1L, "key-1")).thenReturn(Optional.empty());
        when(academicClient.findCandidate(20L)).thenReturn(candidate);
        when(repository.createRequest(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(pending);
        when(repository.lockRequest(100L)).thenReturn(Optional.of(pending));
        when(repository.findActiveEnrollment(1L, 20L)).thenReturn(Optional.empty());
        when(repository.hasScheduleConflict(1L, 30L, candidate.schedules())).thenReturn(true);
        when(repository.findRequestByRequestId("request-1")).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.enroll(1L, "key-1", 20L))
                .isInstanceOfSatisfying(EnrollmentBusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40913));

        verify(redisReservationService, never()).reserve(any(), anyLong(), anyString());
        verify(enrollmentPublisher, never()).publish(any());
    }

    @Test
    void TestPendingIdempotentReplaySkipsRepublish() {
        EnrollmentRequestRecord pending = request("ENROLL", "PENDING", null, null);
        when(repository.findRequestByIdempotency(1L, "key-1")).thenReturn(Optional.of(pending));

        var response = service.enroll(1L, "key-1", 20L);

        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(studentClient, never()).requireEligible(anyLong());
        verify(academicClient, never()).findCandidate(anyLong());
        verify(enrollmentPublisher, never()).publish(any());
    }

    @Test
    void TestIdempotencyKeyWithDifferentActionIsRejected() {
        EnrollmentRequestRecord success = request("DROP", "SUCCESS", null, null);
        when(repository.findRequestByIdempotency(1L, "key-1")).thenReturn(Optional.of(success));

        assertThatThrownBy(() -> service.enroll(1L, "key-1", 20L))
                .isInstanceOfSatisfying(EnrollmentBusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40915));
    }

    @Test
    void TestPublisherFailureReleasesRedisAndPersistsFailure() {
        EnrollmentCandidate candidate = candidate();
        EnrollmentRequestRecord pending = request("ENROLL", "PENDING", null, null);
        EnrollmentRequestRecord failed = request("ENROLL", "FAILED", "50300", "RabbitMQ is unavailable");
        prepareNewEnrollment(candidate, pending);
        when(repository.findRequestByRequestId("request-1")).thenReturn(Optional.of(failed));
        doThrow(new EnrollmentDependencyException("RabbitMQ is unavailable"))
                .when(enrollmentPublisher).publish(any());

        assertThatThrownBy(() -> service.enroll(1L, "key-1", 20L))
                .isInstanceOf(EnrollmentDependencyException.class)
                .hasMessage("RabbitMQ is unavailable");

        verify(redisReservationService).release(20L, 10L, 1L);
        verify(repository).markRequestFailed(100L, 50300, "RabbitMQ is unavailable");
    }

    @Test
    void TestDropActiveEnrollmentRemainsSynchronous() {
        EnrollmentRecord active = enrollment("ENROLLED");
        EnrollmentRequestRecord pending = request("DROP", "PENDING", null, null);
        EnrollmentRequestRecord success = request("DROP", "SUCCESS", null, null);
        when(repository.findRequestByIdempotency(1L, "drop-1")).thenReturn(Optional.empty());
        when(repository.findActiveEnrollment(1L, 20L)).thenReturn(Optional.of(active));
        when(repository.createRequest(anyString(), eq("drop-1"), eq(1L), eq(20L), eq(10L), eq(30L), eq("DROP")))
                .thenReturn(pending);
        when(repository.lockRequest(100L)).thenReturn(Optional.of(pending));
        when(repository.findRequestByRequestId("request-1")).thenReturn(Optional.of(success));

        var response = service.drop(1L, "drop-1", 20L);

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(academicClient).release(10L);
        verify(redisReservationService).release(20L, 10L, 1L);
        verify(repository).dropEnrollment(200L);
        verify(enrollmentPublisher, never()).publish(any());
    }

    @Test
    void TestDropCapacityFailureRestoresRedisReservation() {
        EnrollmentRecord active = enrollment("ENROLLED");
        EnrollmentRequestRecord pending = request("DROP", "PENDING", null, null);
        EnrollmentRequestRecord failed = request("DROP", "FAILED", "40917", "Capacity release underflow");
        when(repository.findRequestByIdempotency(1L, "drop-1")).thenReturn(Optional.empty());
        when(repository.findActiveEnrollment(1L, 20L)).thenReturn(Optional.of(active));
        when(repository.createRequest(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(pending);
        when(repository.lockRequest(100L)).thenReturn(Optional.of(pending));
        when(repository.findRequestByRequestId("request-1")).thenReturn(Optional.of(failed));
        when(redisReservationService.release(20L, 10L, 1L)).thenReturn(true);
        doThrow(new EnrollmentBusinessException(40917, "Capacity release underflow"))
                .when(academicClient).release(10L);

        assertThatThrownBy(() -> service.drop(1L, "drop-1", 20L))
                .isInstanceOfSatisfying(EnrollmentBusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40917));

        verify(redisReservationService).restore(20L, 10L, 1L, "request-1");
        verify(repository, never()).dropEnrollment(anyLong());
    }

    private void prepareNewEnrollment(EnrollmentCandidate candidate, EnrollmentRequestRecord pending) {
        when(repository.findRequestByIdempotency(1L, "key-1")).thenReturn(Optional.empty());
        when(academicClient.findCandidate(20L)).thenReturn(candidate);
        when(repository.createRequest(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(pending);
        when(repository.lockRequest(100L)).thenReturn(Optional.of(pending));
        when(repository.findActiveEnrollment(1L, 20L)).thenReturn(Optional.empty());
        when(repository.hasScheduleConflict(1L, 30L, candidate.schedules())).thenReturn(false);
    }

    private static EnrollmentCandidate candidate() {
        return new EnrollmentCandidate(
                20L, 10L, 30L, 1,
                List.of(new CourseSchedule(1, 1, 2, 1, 16)));
    }

    private static EnrollmentRecord enrollment(String status) {
        return new EnrollmentRecord(
                200L, 1L, 20L, 10L, 30L, status,
                LocalDateTime.of(2026, 9, 1, 10, 0), null);
    }

    private static EnrollmentRequestRecord request(
            String action, String status, String failureCode, String failureMessage) {
        return new EnrollmentRequestRecord(
                100L, "request-1", "key-1", 1L, 20L, 10L, 30L,
                action, status, failureCode, failureMessage,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "PENDING".equals(status) ? null : LocalDateTime.of(2026, 9, 1, 10, 1));
    }
}
