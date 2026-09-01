package com.campusenroll.enrollmentservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.campusenroll.enrollmentservice.api.EnrollmentRequestResponse;
import com.campusenroll.enrollmentservice.api.EnrollmentResponse;
import com.campusenroll.enrollmentservice.client.AcademicClient;
import com.campusenroll.enrollmentservice.client.EnrollmentCandidate;
import com.campusenroll.enrollmentservice.client.StudentEligibilityClient;
import com.campusenroll.enrollmentservice.domain.EnrollmentRecord;
import com.campusenroll.enrollmentservice.domain.EnrollmentRequestRecord;
import com.campusenroll.enrollmentservice.messaging.EnrollmentTask;
import com.campusenroll.enrollmentservice.messaging.RabbitEnrollmentPublisher;
import com.campusenroll.enrollmentservice.repository.EnrollmentRepository;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EnrollmentApplicationService {

    private static final String ENROLL = "ENROLL";
    private static final String DROP = "DROP";

    private final EnrollmentRepository repository;
    private final StudentEligibilityClient studentClient;
    private final AcademicClient academicClient;
    private final RedisReservationService redisReservationService;
    private final RabbitEnrollmentPublisher enrollmentPublisher;
    private final TransactionTemplate transactionTemplate;

    public EnrollmentApplicationService(
            EnrollmentRepository repository,
            StudentEligibilityClient studentClient,
            AcademicClient academicClient,
            RedisReservationService redisReservationService,
            RabbitEnrollmentPublisher enrollmentPublisher,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.studentClient = studentClient;
        this.academicClient = academicClient;
        this.redisReservationService = redisReservationService;
        this.enrollmentPublisher = enrollmentPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    public EnrollmentRequestResponse enroll(long studentId, String idempotencyKey, long courseId) {
        var existing = repository.findRequestByIdempotency(studentId, idempotencyKey);
        if (existing.isPresent()) {
            validateReplay(existing.get(), ENROLL, courseId);
            return EnrollmentRequestResponse.from(existing.get());
        }

        studentClient.requireEligible(studentId);
        EnrollmentCandidate candidate = repository.findActiveEnrollment(studentId, courseId)
                .map(enrollment -> new EnrollmentCandidate(
                        enrollment.courseId(),
                        enrollment.offeringId(),
                        enrollment.semesterId(),
                        0,
                        List.of()))
                .orElseGet(() -> academicClient.findCandidate(courseId));
        Registration registration = register(
                studentId, idempotencyKey, candidate, ENROLL);
        validateReplay(registration.request(), ENROLL, courseId);
        if (!registration.created()) {
            return EnrollmentRequestResponse.from(registration.request());
        }
        return queueEnrollment(registration.request(), candidate);
    }

    public EnrollmentRequestResponse drop(long studentId, String idempotencyKey, long courseId) {
        var existingRequest = repository.findRequestByIdempotency(studentId, idempotencyKey);
        if (existingRequest.isPresent()) {
            validateReplay(existingRequest.get(), DROP, courseId);
            if (!"PENDING".equals(existingRequest.get().status())) {
                return EnrollmentRequestResponse.from(existingRequest.get());
            }
            return processDrop(existingRequest.get());
        }

        EnrollmentRecord enrollment = repository.findActiveEnrollment(studentId, courseId)
                .orElseThrow(() -> new EnrollmentBusinessException(40914, "Course is not currently enrolled"));
        EnrollmentCandidate candidate = new EnrollmentCandidate(
                enrollment.courseId(), enrollment.offeringId(), enrollment.semesterId(), 0, List.of());
        Registration registration = register(studentId, idempotencyKey, candidate, DROP);
        validateReplay(registration.request(), DROP, courseId);
        if (!registration.created() && !"PENDING".equals(registration.request().status())) {
            return EnrollmentRequestResponse.from(registration.request());
        }
        return processDrop(registration.request());
    }

    public List<EnrollmentResponse> findEnrollments(long studentId) {
        return repository.findActiveEnrollments(studentId).stream()
                .map(EnrollmentResponse::from)
                .toList();
    }

    public EnrollmentRequestResponse findRequest(long studentId, String requestId) {
        EnrollmentRequestRecord request = repository.findRequestByRequestId(requestId)
                .filter(candidate -> candidate.studentId() == studentId)
                .orElseThrow(() -> new EnrollmentBusinessException(40400, "Enrollment request not found"));
        return EnrollmentRequestResponse.from(request);
    }

    private EnrollmentRequestResponse queueEnrollment(
            EnrollmentRequestRecord request,
            EnrollmentCandidate candidate) {
        ProcessingResult result = transactionTemplate.execute(status -> {
            EnrollmentRequestRecord locked = repository.lockRequest(request.id())
                    .orElseThrow(() -> new IllegalStateException("Enrollment request disappeared"));
            if (!"PENDING".equals(locked.status())) {
                return ProcessingResult.success(EnrollmentRequestResponse.from(locked));
            }
            repository.lockStudent(locked.studentId());
            if (repository.findActiveEnrollment(locked.studentId(), locked.courseId()).isPresent()) {
                return fail(locked, 40910, "Course is already enrolled");
            }
            if (repository.hasScheduleConflict(
                    locked.studentId(), locked.semesterId(), candidate.schedules())) {
                return fail(locked, 40913, "Course schedule conflicts with an existing enrollment");
            }

            boolean redisReserved = false;
            try {
                redisReservationService.reserve(
                        candidate, locked.studentId(), locked.requestId());
                redisReserved = true;
                enrollmentPublisher.publish(new EnrollmentTask(
                        locked.requestId(),
                        locked.studentId(),
                        locked.courseId(),
                        locked.offeringId(),
                        locked.semesterId(),
                        candidate.schedules(),
                        Instant.now()));
                return ProcessingResult.success(EnrollmentRequestResponse.from(locked));
            } catch (EnrollmentBusinessException exception) {
                compensateRedisReservation(locked, redisReserved, exception);
                return fail(locked, exception.code(), exception.getMessage());
            } catch (EnrollmentDependencyException exception) {
                compensateRedisReservation(locked, redisReserved, exception);
                repository.markRequestFailed(locked.id(), 50300, exception.getMessage());
                return ProcessingResult.failure(reload(locked.requestId()), exception);
            } catch (RuntimeException exception) {
                compensateRedisReservation(locked, redisReserved, exception);
                throw exception;
            }
        });
        if (result == null) {
            throw new IllegalStateException("Enrollment transaction returned no result");
        }
        if (result.failure() != null) {
            throw result.failure();
        }
        return result.response();
    }

    private EnrollmentRequestResponse processDrop(EnrollmentRequestRecord request) {
        ProcessingResult result = transactionTemplate.execute(status -> {
            EnrollmentRequestRecord locked = repository.lockRequest(request.id())
                    .orElseThrow(() -> new IllegalStateException("Enrollment request disappeared"));
            if (!"PENDING".equals(locked.status())) {
                return ProcessingResult.success(EnrollmentRequestResponse.from(locked));
            }
            repository.lockStudent(locked.studentId());
            EnrollmentRecord enrollment = repository.findActiveEnrollment(locked.studentId(), locked.courseId())
                    .orElse(null);
            if (enrollment == null) {
                return fail(locked, 40914, "Course is not currently enrolled");
            }
            boolean redisReleased = false;
            boolean capacityReleased = false;
            try {
                redisReleased = redisReservationService.release(
                        enrollment.courseId(), enrollment.offeringId(), locked.studentId());
                academicClient.release(enrollment.offeringId());
                capacityReleased = true;
                repository.dropEnrollment(enrollment.id());
                repository.markRequestSuccess(locked.id());
                return ProcessingResult.success(reload(locked.requestId()));
            } catch (EnrollmentBusinessException exception) {
                compensateDrop(
                        locked, enrollment, redisReleased, capacityReleased, exception);
                return fail(locked, exception.code(), exception.getMessage());
            } catch (RuntimeException exception) {
                compensateDrop(
                        locked, enrollment, redisReleased, capacityReleased, exception);
                throw exception;
            }
        });
        if (result == null) {
            throw new IllegalStateException("Drop transaction returned no result");
        }
        if (result.failure() != null) {
            throw result.failure();
        }
        return result.response();
    }

    private Registration register(
            long studentId,
            String idempotencyKey,
            EnrollmentCandidate candidate,
            String action) {
        try {
            Registration registration = transactionTemplate.execute(status -> {
                var existing = repository.findRequestByIdempotency(studentId, idempotencyKey);
                if (existing.isPresent()) {
                    return new Registration(existing.get(), false);
                }
                EnrollmentRequestRecord created = repository.createRequest(
                        UUID.randomUUID().toString(),
                        idempotencyKey,
                        studentId,
                        candidate.courseId(),
                        candidate.offeringId(),
                        candidate.semesterId(),
                        action);
                return new Registration(created, true);
            });
            if (registration == null) {
                throw new IllegalStateException("Enrollment request registration returned no result");
            }
            return registration;
        } catch (DuplicateKeyException exception) {
            EnrollmentRequestRecord request = repository.findRequestByIdempotency(studentId, idempotencyKey)
                    .orElseThrow(() -> exception);
            return new Registration(request, false);
        }
    }

    private ProcessingResult fail(
            EnrollmentRequestRecord request,
            int code,
            String message) {
        repository.markRequestFailed(request.id(), code, message);
        EnrollmentRequestResponse response = reload(request.requestId());
        return ProcessingResult.failure(response, new EnrollmentBusinessException(code, message));
    }

    private EnrollmentRequestResponse reload(String requestId) {
        return repository.findRequestByRequestId(requestId)
                .map(EnrollmentRequestResponse::from)
                .orElseThrow(() -> new IllegalStateException("Enrollment request could not be reloaded"));
    }

    private void validateReplay(EnrollmentRequestRecord request, String action, long courseId) {
        if (!action.equals(request.action()) || courseId != request.courseId()) {
            throw new EnrollmentBusinessException(
                    40915,
                    "Idempotency key was already used for a different enrollment operation");
        }
    }

    private void compensateRedisReservation(
            EnrollmentRequestRecord request,
            boolean redisReserved,
            RuntimeException original) {
        if (redisReserved) {
            try {
                redisReservationService.release(
                        request.courseId(), request.offeringId(), request.studentId());
            } catch (RuntimeException compensationFailure) {
                original.addSuppressed(compensationFailure);
            }
        }
    }

    private void compensateDrop(
            EnrollmentRequestRecord request,
            EnrollmentRecord enrollment,
            boolean redisReleased,
            boolean capacityReleased,
            RuntimeException original) {
        if (capacityReleased) {
            try {
                academicClient.reserve(enrollment.offeringId());
            } catch (RuntimeException compensationFailure) {
                original.addSuppressed(compensationFailure);
            }
        }
        if (redisReleased) {
            try {
                redisReservationService.restore(
                        enrollment.courseId(),
                        enrollment.offeringId(),
                        request.studentId(),
                        request.requestId());
            } catch (RuntimeException compensationFailure) {
                original.addSuppressed(compensationFailure);
            }
        }
    }

    private record Registration(EnrollmentRequestRecord request, boolean created) {
    }

    private record ProcessingResult(
            EnrollmentRequestResponse response,
            RuntimeException failure) {

        private static ProcessingResult success(EnrollmentRequestResponse response) {
            return new ProcessingResult(response, null);
        }

        private static ProcessingResult failure(
                EnrollmentRequestResponse response,
                RuntimeException failure) {
            return new ProcessingResult(response, failure);
        }
    }
}
