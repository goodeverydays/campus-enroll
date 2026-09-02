package com.campusenroll.enrollmentservice.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.enrollmentservice.client.CourseSchedule;
import com.campusenroll.enrollmentservice.domain.EnrollmentRecord;
import com.campusenroll.enrollmentservice.domain.EnrollmentRequestRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEnrollmentRepository implements EnrollmentRepository {

    private static final String REQUEST_SELECT = """
            SELECT id, request_id, idempotency_key, student_id, course_id, offering_id,
                   semester_id, action, status, failure_code, failure_message,
                   requested_at, completed_at
            FROM enrollment_request
            """;

    private static final String ENROLLMENT_SELECT = """
            SELECT id, student_id, course_id, offering_id, semester_id, source_request_id, status,
                   enrolled_at, dropped_at
            FROM enrollment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcEnrollmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<EnrollmentRequestRecord> findRequestByIdempotency(long studentId, String idempotencyKey) {
        return findRequest(
                REQUEST_SELECT + " WHERE student_id = :studentId AND idempotency_key = :idempotencyKey",
                Map.of("studentId", studentId, "idempotencyKey", idempotencyKey));
    }

    @Override
    public Optional<EnrollmentRequestRecord> findRequestByRequestId(String requestId) {
        return findRequest(REQUEST_SELECT + " WHERE request_id = :requestId", Map.of("requestId", requestId));
    }

    @Override
    public Optional<EnrollmentRequestRecord> lockRequest(long requestRowId) {
        return findRequest(
                REQUEST_SELECT + " WHERE id = :requestRowId FOR UPDATE",
                Map.of("requestRowId", requestRowId));
    }

    @Override
    public EnrollmentRequestRecord createRequest(
            String requestId,
            String idempotencyKey,
            long studentId,
            long courseId,
            long offeringId,
            long semesterId,
            String action) {
        jdbcTemplate.update("""
                INSERT INTO enrollment_request (
                    request_id, idempotency_key, student_id, course_id,
                    offering_id, semester_id, action, status)
                VALUES (
                    :requestId, :idempotencyKey, :studentId, :courseId,
                    :offeringId, :semesterId, :action, 'PENDING')
                """, Map.of(
                "requestId", requestId,
                "idempotencyKey", idempotencyKey,
                "studentId", studentId,
                "courseId", courseId,
                "offeringId", offeringId,
                "semesterId", semesterId,
                "action", action));
        return findRequestByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("Created enrollment request could not be reloaded"));
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
    public Optional<EnrollmentRecord> findActiveEnrollment(long studentId, long courseId) {
        return findOneEnrollment(
                ENROLLMENT_SELECT + """
                        WHERE student_id = :studentId
                          AND course_id = :courseId
                          AND status = 'ENROLLED'
                        """,
                Map.of("studentId", studentId, "courseId", courseId));
    }

    @Override
    public Optional<EnrollmentRecord> findEnrollment(long studentId, long courseId, long semesterId) {
        return findOneEnrollment(
                ENROLLMENT_SELECT + """
                        WHERE student_id = :studentId
                          AND course_id = :courseId
                          AND semester_id = :semesterId
                        """,
                Map.of("studentId", studentId, "courseId", courseId, "semesterId", semesterId));
    }

    @Override
    public boolean hasScheduleConflict(long studentId, long semesterId, List<CourseSchedule> schedules) {
        for (CourseSchedule schedule : schedules) {
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
    public EnrollmentRecord createEnrollment(
            long studentId,
            long courseId,
            long offeringId,
            long semesterId,
            List<CourseSchedule> schedules) {
        var keyHolder = new GeneratedKeyHolder();
        var parameters = new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("courseId", courseId)
                .addValue("offeringId", offeringId)
                .addValue("semesterId", semesterId);
        jdbcTemplate.update("""
                INSERT INTO enrollment (student_id, course_id, offering_id, semester_id, status)
                VALUES (:studentId, :courseId, :offeringId, :semesterId, 'ENROLLED')
                """, parameters, keyHolder, new String[] {"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Enrollment insert did not return an ID");
        }
        long enrollmentId = key.longValue();
        insertSchedules(enrollmentId, schedules);
        return requireEnrollment(enrollmentId);
    }

    @Override
    public EnrollmentRecord reactivateEnrollment(
            long enrollmentId,
            long offeringId,
            List<CourseSchedule> schedules) {
        jdbcTemplate.update("""
                UPDATE enrollment
                SET offering_id = :offeringId,
                    status = 'ENROLLED',
                    enrolled_at = CURRENT_TIMESTAMP(3),
                    dropped_at = NULL,
                    version = version + 1
                WHERE id = :enrollmentId
                  AND status = 'DROPPED'
                """, Map.of("enrollmentId", enrollmentId, "offeringId", offeringId));
        jdbcTemplate.update(
                "DELETE FROM enrollment_schedule WHERE enrollment_id = :enrollmentId",
                Map.of("enrollmentId", enrollmentId));
        insertSchedules(enrollmentId, schedules);
        return requireEnrollment(enrollmentId);
    }

    @Override
    public EnrollmentRecord dropEnrollment(long enrollmentId) {
        int updated = jdbcTemplate.update("""
                UPDATE enrollment
                SET status = 'DROPPED',
                    dropped_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = :enrollmentId
                  AND status = 'ENROLLED'
                """, Map.of("enrollmentId", enrollmentId));
        if (updated != 1) {
            throw new IllegalStateException("Enrollment was not active when dropped");
        }
        return requireEnrollment(enrollmentId);
    }

    @Override
    public List<EnrollmentRecord> findActiveEnrollments(long studentId) {
        return jdbcTemplate.query(
                ENROLLMENT_SELECT + """
                        WHERE student_id = :studentId
                          AND status = 'ENROLLED'
                        ORDER BY enrolled_at DESC, id DESC
                        """,
                Map.of("studentId", studentId),
                ENROLLMENT_ROW_MAPPER);
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
                """, Map.of(
                "requestRowId", requestRowId,
                "failureCode", Integer.toString(failureCode),
                "failureMessage", failureMessage));
    }

    private Optional<EnrollmentRequestRecord> findRequest(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, REQUEST_ROW_MAPPER).stream().findFirst();
    }

    private Optional<EnrollmentRecord> findOneEnrollment(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, ENROLLMENT_ROW_MAPPER).stream().findFirst();
    }

    private EnrollmentRecord requireEnrollment(long enrollmentId) {
        return findOneEnrollment(
                ENROLLMENT_SELECT + " WHERE id = :enrollmentId",
                Map.of("enrollmentId", enrollmentId))
                .orElseThrow(() -> new IllegalStateException("Enrollment could not be reloaded"));
    }

    private void insertSchedules(long enrollmentId, List<CourseSchedule> schedules) {
        for (CourseSchedule schedule : schedules) {
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

    private static final RowMapper<EnrollmentRequestRecord> REQUEST_ROW_MAPPER =
            JdbcEnrollmentRepository::mapRequest;

    private static final RowMapper<EnrollmentRecord> ENROLLMENT_ROW_MAPPER =
            JdbcEnrollmentRepository::mapEnrollment;

    private static EnrollmentRequestRecord mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EnrollmentRequestRecord(
                resultSet.getLong("id"),
                resultSet.getString("request_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getLong("student_id"),
                resultSet.getLong("course_id"),
                resultSet.getLong("offering_id"),
                resultSet.getLong("semester_id"),
                resultSet.getString("action"),
                resultSet.getString("status"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                resultSet.getObject("requested_at", java.time.LocalDateTime.class),
                resultSet.getObject("completed_at", java.time.LocalDateTime.class));
    }

    private static EnrollmentRecord mapEnrollment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EnrollmentRecord(
                resultSet.getLong("id"),
                resultSet.getLong("student_id"),
                resultSet.getLong("course_id"),
                resultSet.getLong("offering_id"),
                resultSet.getLong("semester_id"),
                resultSet.getString("source_request_id"),
                resultSet.getString("status"),
                resultSet.getObject("enrolled_at", java.time.LocalDateTime.class),
                resultSet.getObject("dropped_at", java.time.LocalDateTime.class));
    }
}
