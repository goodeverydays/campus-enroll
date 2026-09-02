package com.campusenroll.enrollmentworker.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.campusenroll.enrollmentworker.client.CourseCapacityClient;
import com.campusenroll.enrollmentworker.domain.WorkerEnrollment;
import com.campusenroll.enrollmentworker.domain.WorkerEnrollmentRequest;
import com.campusenroll.enrollmentworker.messaging.EnrollmentTask;
import com.campusenroll.enrollmentworker.messaging.EnrollmentTaskSchedule;
import com.campusenroll.enrollmentworker.repository.EnrollmentWorkerRepository;
import com.campusenroll.enrollmentworker.support.WorkerBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class EnrollmentWorkerServiceTest {

    @Mock private EnrollmentWorkerRepository repository;
    @Mock private CourseCapacityClient courseCapacityClient;
    @Mock private RedisReservationCompensator redisCompensator;
    @Mock private TransactionTemplate transactionTemplate;

    private EnrollmentWorkerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        service = new EnrollmentWorkerService(
                repository, courseCapacityClient, redisCompensator, transactionTemplate);
    }

    @Test
    void TestPendingTaskCreatesEnrollmentAndMarksSuccess() {
        preparePending();
        when(repository.findEnrollment(1L, 20L, 30L)).thenReturn(Optional.empty());

        service.process(task());

        verify(courseCapacityClient).reserve(10L, "request-1");
        verify(repository).createEnrollment(
                1L, 20L, 10L, 30L, "request-1", task().schedules());
        verify(repository).markRequestSuccess(100L);
        verify(redisCompensator, never()).release(anyLong(), anyLong(), anyLong());
    }

    @Test
    void TestCompletedTaskSkipsDuplicateDelivery() {
        when(repository.lockRequest("request-1")).thenReturn(Optional.of(request("SUCCESS")));

        service.process(task());

        verify(repository, never()).lockStudent(anyLong());
        verify(courseCapacityClient, never()).reserve(anyLong(), any());
        verify(repository, never()).markRequestSuccess(anyLong());
    }

    @Test
    void TestCapacityBusinessFailureMarksFailedAndReleasesRedis() {
        preparePending();
        doThrow(new WorkerBusinessException(40911, "Offering is full"))
                .when(courseCapacityClient).reserve(10L, "request-1");

        service.process(task());

        verify(redisCompensator).release(20L, 10L, 1L);
        verify(repository).markRequestFailed(100L, 40911, "Offering is full");
        verify(courseCapacityClient, never()).release(anyLong(), any());
    }

    @Test
    void TestPersistenceFailureCompensatesReservationsAndRethrows() {
        preparePending();
        when(repository.findEnrollment(1L, 20L, 30L)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("database write failed"))
                .when(repository).createEnrollment(
                        1L, 20L, 10L, 30L, "request-1", task().schedules());

        assertThatThrownBy(() -> service.process(task()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database write failed");

        verify(courseCapacityClient).release(10L, "request-1");
        verify(redisCompensator, never()).release(anyLong(), anyLong(), anyLong());
        verify(repository, never()).markRequestSuccess(anyLong());
    }

    @Test
    void TestExistingEnrollmentFailsWithoutCapacityMutation() {
        when(repository.lockRequest("request-1")).thenReturn(Optional.of(request("PENDING")));
        when(repository.findActiveEnrollment(1L, 20L))
                .thenReturn(Optional.of(new WorkerEnrollment(
                        200L, 1L, 20L, 10L, 30L, "request-1", "ENROLLED")));

        service.process(task());

        verify(redisCompensator).release(20L, 10L, 1L);
        verify(repository).markRequestFailed(100L, 40910, "Course is already enrolled");
        verify(courseCapacityClient, never()).reserve(anyLong(), any());
    }

    @Test
    void TestRetryExhaustionReleasesReservationsAndMarksFailed() {
        when(repository.lockRequest("request-1")).thenReturn(Optional.of(request("PENDING")));

        service.failAfterRetries(task(), "Retry attempts exhausted", 3, "WorkerDependencyException");

        verify(courseCapacityClient).release(10L, "request-1");
        verify(redisCompensator).release(20L, 10L, 1L);
        verify(repository).markRequestFailed(100L, 50301, "Retry attempts exhausted");
        verify(repository).recordDeadLetter("request-1", 3, "WorkerDependencyException");
    }

    @Test
    void TestRetryExhaustionForUnknownRequestNeedsNoCompensation() {
        when(repository.lockRequest("request-1")).thenReturn(Optional.empty());

        service.failAfterRetries(task(), "Retry attempts exhausted", 3, "IllegalStateException");

        verify(courseCapacityClient, never()).release(anyLong(), any());
        verify(redisCompensator, never()).release(anyLong(), anyLong(), anyLong());
        verify(repository, never()).markRequestFailed(anyLong(), anyInt(), any());
        verify(repository, never()).recordDeadLetter(any(), anyInt(), any());
    }

    private void preparePending() {
        when(repository.lockRequest("request-1")).thenReturn(Optional.of(request("PENDING")));
        when(repository.findActiveEnrollment(1L, 20L)).thenReturn(Optional.empty());
        when(repository.hasScheduleConflict(1L, 30L, task().schedules())).thenReturn(false);
    }

    private static WorkerEnrollmentRequest request(String status) {
        return new WorkerEnrollmentRequest(
                100L, "request-1", 1L, 20L, 10L, 30L, "ENROLL", status);
    }

    private static EnrollmentTask task() {
        return new EnrollmentTask(
                "request-1", 1L, 20L, 10L, 30L,
                List.of(new EnrollmentTaskSchedule(1, 1, 2, 1, 16)), Instant.EPOCH);
    }
}
