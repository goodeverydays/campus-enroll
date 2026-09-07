package com.campusenroll.enrollmentworker.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.enrollmentworker.domain.WorkerEnrollment;
import com.campusenroll.enrollmentworker.domain.WorkerEnrollmentRequest;
import com.campusenroll.enrollmentworker.messaging.EnrollmentTaskSchedule;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEnrollmentWorkerRepository implements EnrollmentWorkerRepository {

    private static final String REQUEST_SELECT = """
            SELECT id, request_id, student_id, course_id, offering_id,
                   semester_id, action, status
            FROM enrollment_request
            """;

    private static final String ENROLLMENT_SELECT = """
            SELECT id, student_id, course_id, offering_id, semester_id, source_request_id, status
            FROM enrollment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcEnrollmentWorkerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WorkerEnrollmentRequest> lockRequest(String requestId) {
        return jdbcTemplate.query(
                REQUEST_SELECT + " WHERE request_id = :requestId FOR UPDATE",
                Map.of("requestId", requestId),
                REQUEST_ROW_MAPPER).stream().findFirst();
    }

    @Override
    public void lockStudent(long studentId) {
        jdbcTemplate.update("""
                INSERT INTO student_enrollment_lock (student_id)
                VALUES (:studentId)
                ON DUPLICATE KEY UPDATE student_id = VALUES(student_id)
                """, Map.of("studentId", studentId));
        jdbcTemplate.queryForObject("""
                SELECT student_id
                FROM student_enrollment_lock
                WHERE student_id = :studentId
                FOR UPDATE
                """, Map.of("studentId", studentId), Long.class);
    }

    @Override
    public Optional<WorkerEnrollment> findActiveEnrollment(long studentId, long courseId) {
        return findEnrollment(ENROLLMENT_SELECT + """
                WHERE student_id = :studentId
                  AND course_id = :courseId
                  AND status = 'ENROLLED'
                """, Map.of("studentId", studentId, "courseId", courseId));
    }

    @Override
    public Optional<WorkerEnrollment> findEnrollment(long studentId, long courseId, long semesterId) {
        return findEnrollment(ENROLLMENT_SELECT + """
                WHERE student_id = :studentId
                  AND course_id = :courseId
                  AND semester_id = :semesterId
                """, Map.of("studentId", studentId, "courseId", courseId, "semesterId", semesterId));
    }

    @Override
    public boolean hasScheduleConflict(
            long studentId,
            long semesterId,
            List<EnrollmentTaskSchedule> schedules) {
        for (EnrollmentTaskSchedule schedule : schedules) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM enrollment e
                    JOIN enrollment_schedule es ON es.enrollment_id = e.id
                    WHERE e.student_id = :studentId
                      AND e.semester_id = :semesterId
                      AND e.status = 'ENROLLED'
                      AND es.day_of_week = :dayOfWeek
                      AND es.start_section <= :endSection
                      AND es.end_section >= :startSection
                      AND es.start_week <= :endWeek
                      AND es.end_week >= :startWeek
                    """, Map.of(
                    "studentId", studentId,
                    "semesterId", semesterId,
                    "dayOfWeek", schedule.dayOfWeek(),
                    "startSection", schedule.startSection(),
                    "endSection", schedule.endSection(),
                    "startWeek", schedule.startWeek(),
                    "endWeek", schedule.endWeek()), Integer.class);
            if (count != null && count > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void createEnrollment(
            long studentId,
            long courseId,
            long offeringId,
            long semesterId,
            String sourceRequestId,
            List<EnrollmentTaskSchedule> schedules) {
        var keyHolder = new GeneratedKeyHolder();
        var parameters = new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("courseId", courseId)
                .addValue("offeringId", offeringId)
                .addValue("semesterId", semesterId)
                .addValue("sourceRequestId", sourceRequestId);
        jdbcTemplate.update("""
                INSERT INTO enrollment (
                    student_id, course_id, offering_id, semester_id, source_request_id, status)
                VALUES (
                    :studentId, :courseId, :offeringId, :semesterId, :sourceRequestId, 'ENROLLED')
                """, parameters, keyHolder, new String[] {"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Enrollment insert did not return an ID");
        }
        insertSchedules(key.longValue(), schedules);
    }

    @Override
    public void reactivateEnrollment(
            long enrollmentId,
            long offeringId,
            String sourceRequestId,
            List<EnrollmentTaskSchedule> schedules) {
        int updated = jdbcTemplate.update("""
                UPDATE enrollment
                SET offering_id = :offeringId,
                    source_request_id = :sourceRequestId,
                    status = 'ENROLLED',
                    enrolled_at = CURRENT_TIMESTAMP(3),
                    dropped_at = NULL,
                    version = version + 1
                WHERE id = :enrollmentId
                  AND status = 'DROPPED'
                """, Map.of(
                "enrollmentId", enrollmentId,
                "offeringId", offeringId,
                "sourceRequestId", sourceRequestId));
        if (updated != 1) {
            throw new IllegalStateException("Enrollment was not dropped when reactivated");
        }
        jdbcTemplate.update(
                "DELETE FROM enrollment_schedule WHERE enrollment_id = :enrollmentId",
                Map.of("enrollmentId", enrollmentId));
        insertSchedules(enrollmentId, schedules);
    }

    @Override
    public void markRequestSuccess(long requestRowId) {
        jdbcTemplate.update("""
                UPDATE enrollment_request
                SET status = 'SUCCESS',
                    failure_code = NULL,
                    failure_message = NULL,
                    completed_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = :requestRowId
                  AND status = 'PENDING'
                """, Map.of("requestRowId", requestRowId));
    }

    @Override
    public void markRequestFailed(long requestRowId, int failureCode, String failureMessage) {
        jdbcTemplate.update("""
                UPDATE enrollment_request
                SET status = 'FAILED',
                    failure_code = :failureCode,
                    failure_message = :failureMessage,
                    completed_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = :requestRowId
                  AND status = 'PENDING'
                """, Map.of(
                "requestRowId", requestRowId,
                "failureCode", Integer.toString(failureCode),
                "failureMessage", failureMessage));
    }

    @Override
    public void recordDeadLetter(String requestId, int attemptCount, String failureType) {
        jdbcTemplate.update("""
                INSERT INTO enrollment_dead_letter (
                    request_id, attempt_count, failure_type, failed_at)
                VALUES (
                    :requestId, :attemptCount, :failureType, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                    attempt_count = VALUES(attempt_count),
                    failure_type = VALUES(failure_type),
                    failed_at = VALUES(failed_at)
                """, Map.of(
                "requestId", requestId,
                "attemptCount", attemptCount,
                "failureType", failureType));
    }

    private Optional<WorkerEnrollment> findEnrollment(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, ENROLLMENT_ROW_MAPPER).stream().findFirst();
    }

    private void insertSchedules(long enrollmentId, List<EnrollmentTaskSchedule> schedules) {
        for (EnrollmentTaskSchedule schedule : schedules) {
            jdbcTemplate.update("""
                    INSERT INTO enrollment_schedule (
                        enrollment_id, day_of_week, start_section,
                        end_section, start_week, end_week)
                    VALUES (
                        :enrollmentId, :dayOfWeek, :startSection,
                        :endSection, :startWeek, :endWeek)
                    """, Map.of(
                    "enrollmentId", enrollmentId,
                    "dayOfWeek", schedule.dayOfWeek(),
                    "startSection", schedule.startSection(),
                    "endSection", schedule.endSection(),
                    "startWeek", schedule.startWeek(),
                    "endWeek", schedule.endWeek()));
        }
    }

    private static final RowMapper<WorkerEnrollmentRequest> REQUEST_ROW_MAPPER =
            JdbcEnrollmentWorkerRepository::mapRequest;
    private static final RowMapper<WorkerEnrollment> ENROLLMENT_ROW_MAPPER =
            JdbcEnrollmentWorkerRepository::mapEnrollment;

    private static WorkerEnrollmentRequest mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkerEnrollmentRequest(
                resultSet.getLong("id"),
                resultSet.getString("request_id"),
                resultSet.getLong("student_id"),
                resultSet.getLong("course_id"),
                resultSet.getLong("offering_id"),
                resultSet.getLong("semester_id"),
                resultSet.getString("action"),
                resultSet.getString("status"));
    }

    private static WorkerEnrollment mapEnrollment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkerEnrollment(
                resultSet.getLong("id"),
                resultSet.getLong("student_id"),
                resultSet.getLong("course_id"),
                resultSet.getLong("offering_id"),
                resultSet.getLong("semester_id"),
                resultSet.getString("source_request_id"),
                resultSet.getString("status"));
    }
}
