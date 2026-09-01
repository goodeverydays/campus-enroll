package com.campusenroll.enrollmentservice.service;

import java.util.List;
import java.util.UUID;

import com.campusenroll.enrollmentservice.api.EnrollmentRequestResponse;
import com.campusenroll.enrollmentservice.api.EnrollmentResponse;
import com.campusenroll.enrollmentservice.client.AcademicClient;
import com.campusenroll.enrollmentservice.client.EnrollmentCandidate;
import com.campusenroll.enrollmentservice.client.StudentEligibilityClient;
import com.campusenroll.enrollmentservice.domain.EnrollmentRecord;
import com.campusenroll.enrollmentservice.domain.EnrollmentRequestRecord;
import com.campusenroll.enrollmentservice.repository.EnrollmentRepository;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
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
    private final TransactionTemplate transactionTemplate;

    public EnrollmentApplicationService(
            EnrollmentRepository repository,
            StudentEligibilityClient studentClient,
            AcademicClient academicClient,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.studentClient = studentClient;
        this.academicClient = academicClient;
        this.transactionTemplate = transactionTemplate;
    }

    public EnrollmentRequestResponse enroll(long studentId, String idempotencyKey, long courseId) {
        var existing = repository.findRequestByIdempotency(studentId, idempotencyKey);
        if (existing.isPresent()) {
            validateReplay(existing.get(), ENROLL, courseId);
            if (!"PENDING".equals(existing.get().status())) {
                return EnrollmentRequestResponse.from(existing.get());
            }
            studentClient.requireEligible(studentId);
            EnrollmentCandidate candidate = academicClient.findOffering(
                    courseId, existing.get().offeringId());
            return processEnrollment(existing.get(), candidate);
        }

        studentClient.requireEligible(studentId);
        EnrollmentCandidate candidate = repository.findActiveEnrollment(studentId, courseId)
                .map(enrollment -> new EnrollmentCandidate(
                        enrollment.courseId(),
                        enrollment.offeringId(),
                        enrollment.semesterId(),
                        List.of()))
                .orElseGet(() -> academicClient.findCandidate(courseId));
        Registration registration = register(
                studentId, idempotencyKey, candidate, ENROLL);
        validateReplay(registration.request(), ENROLL, courseId);
        if (!registration.created() && !"PENDING".equals(registration.request().status())) {
            return EnrollmentRequestResponse.from(registration.request());
        }
        return processEnrollment(registration.request(), candidate);
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
                enrollment.courseId(), enrollment.offeringId(), enrollment.semesterId(), List.of());
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

    private EnrollmentRequestResponse processEnrollment(
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

            boolean reserved = false;
            try {
                academicClient.reserve(locked.offeringId());
                reserved = true;
                var previous = repository.findEnrollment(
                        locked.studentId(), locked.courseId(), locked.semesterId());
                if (previous.isPresent()) {
                    repository.reactivateEnrollment(
                            previous.get().id(), locked.offeringId(), candidate.schedules());
                } else {
                    repository.createEnrollment(
                            locked.studentId(), locked.courseId(), locked.offeringId(),
                            locked.semesterId(), candidate.schedules());
                }
                repository.markRequestSuccess(locked.id());
                return ProcessingResult.success(reload(locked.requestId()));
            } catch (EnrollmentBusinessException exception) {
                if (reserved) {
                    compensateRelease(locked.offeringId(), exception);
                }
                return fail(locked, exception.code(), exception.getMessage());
            } catch (RuntimeException exception) {
                if (reserved) {
                    compensateRelease(locked.offeringId(), exception);
                }
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
            try {
                academicClient.release(enrollment.offeringId());
                repository.dropEnrollment(enrollment.id());
                repository.markRequestSuccess(locked.id());
                return ProcessingResult.success(reload(locked.requestId()));
            } catch (EnrollmentBusinessException exception) {
                return fail(locked, exception.code(), exception.getMessage());
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

    private void compensateRelease(long offeringId, RuntimeException original) {
        try {
            academicClient.release(offeringId);
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
        }
    }

    private record Registration(EnrollmentRequestRecord request, boolean created) {
    }

    private record ProcessingResult(
            EnrollmentRequestResponse response,
            EnrollmentBusinessException failure) {

        private static ProcessingResult success(EnrollmentRequestResponse response) {
            return new ProcessingResult(response, null);
        }

        private static ProcessingResult failure(
                EnrollmentRequestResponse response,
                EnrollmentBusinessException failure) {
            return new ProcessingResult(response, failure);
        }
    }
}
