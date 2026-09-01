package com.campusenroll.enrollmentworker.repository;

import java.util.List;
import java.util.Optional;

import com.campusenroll.enrollmentworker.domain.WorkerEnrollment;
import com.campusenroll.enrollmentworker.domain.WorkerEnrollmentRequest;
import com.campusenroll.enrollmentworker.messaging.EnrollmentTaskSchedule;

public interface EnrollmentWorkerRepository {

    Optional<WorkerEnrollmentRequest> lockRequest(String requestId);

    void lockStudent(long studentId);

    Optional<WorkerEnrollment> findActiveEnrollment(long studentId, long courseId);

    Optional<WorkerEnrollment> findEnrollment(long studentId, long courseId, long semesterId);

    boolean hasScheduleConflict(long studentId, long semesterId, List<EnrollmentTaskSchedule> schedules);

    void createEnrollment(
            long studentId,
            long courseId,
            long offeringId,
            long semesterId,
            List<EnrollmentTaskSchedule> schedules);

    void reactivateEnrollment(
            long enrollmentId,
            long offeringId,
            List<EnrollmentTaskSchedule> schedules);

    void markRequestSuccess(long requestRowId);

    void markRequestFailed(long requestRowId, int failureCode, String failureMessage);
}
