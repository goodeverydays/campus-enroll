package com.campusenroll.enrollmentservice.repository;

import java.util.List;
import java.util.Optional;

import com.campusenroll.enrollmentservice.client.CourseSchedule;
import com.campusenroll.enrollmentservice.domain.EnrollmentRecord;
import com.campusenroll.enrollmentservice.domain.EnrollmentRequestRecord;

public interface EnrollmentRepository {

    Optional<EnrollmentRequestRecord> findRequestByIdempotency(long studentId, String idempotencyKey);

    Optional<EnrollmentRequestRecord> findRequestByRequestId(String requestId);

    Optional<EnrollmentRequestRecord> lockRequest(long requestRowId);

    EnrollmentRequestRecord createRequest(
            String requestId,
            String idempotencyKey,
            long studentId,
            long courseId,
            long offeringId,
            long semesterId,
            String action);

    void lockStudent(long studentId);

    Optional<EnrollmentRecord> findActiveEnrollment(long studentId, long courseId);

    Optional<EnrollmentRecord> findEnrollment(long studentId, long courseId, long semesterId);

    boolean hasScheduleConflict(long studentId, long semesterId, List<CourseSchedule> schedules);

    EnrollmentRecord createEnrollment(
            long studentId,
            long courseId,
            long offeringId,
            long semesterId,
            List<CourseSchedule> schedules);

    EnrollmentRecord reactivateEnrollment(
            long enrollmentId,
            long offeringId,
            List<CourseSchedule> schedules);

    EnrollmentRecord dropEnrollment(long enrollmentId);

    List<EnrollmentRecord> findActiveEnrollments(long studentId);

    void markRequestSuccess(long requestRowId);

    void markRequestFailed(long requestRowId, int failureCode, String failureMessage);
}
