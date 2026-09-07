package com.campusenroll.enrollmentworker.service;

import com.campusenroll.enrollmentworker.client.CourseCapacityClient;
import com.campusenroll.enrollmentworker.domain.WorkerEnrollment;
import com.campusenroll.enrollmentworker.domain.WorkerEnrollmentRequest;
import com.campusenroll.enrollmentworker.messaging.EnrollmentTask;
import com.campusenroll.enrollmentworker.repository.EnrollmentWorkerRepository;
import com.campusenroll.enrollmentworker.support.WorkerBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EnrollmentWorkerService {

    private final EnrollmentWorkerRepository repository;
    private final CourseCapacityClient courseCapacityClient;
    private final RedisReservationCompensator redisCompensator;
    private final TransactionTemplate transactionTemplate;

    public EnrollmentWorkerService(
            EnrollmentWorkerRepository repository,
            CourseCapacityClient courseCapacityClient,
            RedisReservationCompensator redisCompensator,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.courseCapacityClient = courseCapacityClient;
        this.redisCompensator = redisCompensator;
        this.transactionTemplate = transactionTemplate;
    }

    public void process(EnrollmentTask task) {
        Boolean processed = transactionTemplate.execute(status -> processInTransaction(task));
        if (processed == null) {
            throw new IllegalStateException("Enrollment worker transaction returned no result");
        }
    }

    private boolean processInTransaction(EnrollmentTask task) {
        WorkerEnrollmentRequest request = repository.lockRequest(task.requestId())
                .orElseThrow(() -> new IllegalStateException("Enrollment request does not exist"));
        validateContract(request, task);
        if (!"PENDING".equals(request.status())) {
            return false;
        }

        repository.lockStudent(request.studentId());
        if (repository.findActiveEnrollment(request.studentId(), request.courseId()).isPresent()) {
            failAndReleaseRedis(request, 40910, "Course is already enrolled");
            return true;
        }
        if (repository.hasScheduleConflict(request.studentId(), request.semesterId(), task.schedules())) {
            failAndReleaseRedis(
                    request,
                    40913,
                    "Course schedule conflicts with an existing enrollment");
            return true;
        }

        boolean capacityReserved = false;
        try {
            courseCapacityClient.reserve(request.offeringId(), request.requestId());
            capacityReserved = true;
            WorkerEnrollment previous = repository.findEnrollment(
                    request.studentId(), request.courseId(), request.semesterId()).orElse(null);
            if (previous == null) {
                repository.createEnrollment(
                        request.studentId(),
                        request.courseId(),
                        request.offeringId(),
                        request.semesterId(),
                        request.requestId(),
                        task.schedules());
            } else {
                repository.reactivateEnrollment(
                        previous.id(), request.offeringId(), request.requestId(), task.schedules());
            }
            repository.markRequestSuccess(request.id());
            return true;
        } catch (WorkerBusinessException exception) {
            compensateCapacity(request, capacityReserved, exception);
            releaseRedis(request);
            repository.markRequestFailed(request.id(), exception.code(), exception.getMessage());
            return true;
        } catch (RuntimeException exception) {
            compensateCapacity(request, capacityReserved, exception);
            throw exception;
        }
    }

    public void failAfterRetries(
            EnrollmentTask task,
            String failureMessage,
            int attemptCount,
            String failureType) {
        Boolean finalized = transactionTemplate.execute(status -> {
            WorkerEnrollmentRequest request = repository.lockRequest(task.requestId()).orElse(null);
            if (request == null || !matchesContract(request, task)) {
                return false;
            }
            if (!"PENDING".equals(request.status())) {
                return false;
            }
            courseCapacityClient.release(request.offeringId(), request.requestId());
            releaseRedis(request);
            repository.markRequestFailed(request.id(), 50301, failureMessage);
            repository.recordDeadLetter(request.requestId(), attemptCount, failureType);
            return true;
        });
        if (finalized == null) {
            throw new IllegalStateException("Enrollment retry finalization returned no result");
        }
    }

    private void validateContract(WorkerEnrollmentRequest request, EnrollmentTask task) {
        if (!matchesContract(request, task)) {
            throw new IllegalStateException("Enrollment task does not match its database request");
        }
    }

    private static boolean matchesContract(WorkerEnrollmentRequest request, EnrollmentTask task) {
        return "ENROLL".equals(request.action())
                && request.studentId() == task.studentId()
                && request.courseId() == task.courseId()
                && request.offeringId() == task.offeringId()
                && request.semesterId() == task.semesterId();
    }

    private void failAndReleaseRedis(
            WorkerEnrollmentRequest request,
            int code,
            String message) {
        releaseRedis(request);
        repository.markRequestFailed(request.id(), code, message);
    }

    private void compensateCapacity(
            WorkerEnrollmentRequest request,
            boolean capacityReserved,
            RuntimeException original) {
        if (capacityReserved) {
            try {
                courseCapacityClient.release(request.offeringId(), request.requestId());
            } catch (RuntimeException compensationFailure) {
                original.addSuppressed(compensationFailure);
            }
        }
    }

    private void releaseRedis(WorkerEnrollmentRequest request) {
        redisCompensator.release(
                request.courseId(), request.offeringId(), request.studentId());
    }
}
