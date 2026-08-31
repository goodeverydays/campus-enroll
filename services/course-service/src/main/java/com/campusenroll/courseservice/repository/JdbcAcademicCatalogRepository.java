package com.campusenroll.courseservice.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.domain.CourseSchedule;
import com.campusenroll.courseservice.domain.Semester;
import com.campusenroll.courseservice.domain.Teacher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAcademicCatalogRepository implements AcademicCatalogRepository {

    private static final String OFFERING_QUERY = """
            SELECT o.id,
                   c.id AS course_id,
                   c.code AS course_code,
                   c.name AS course_name,
                   s.id AS semester_id,
                   s.name AS semester_name,
                   t.id AS teacher_id,
                   t.name AS teacher_name,
                   o.section_no,
                   o.capacity,
                   o.selected_count,
                   o.status
            FROM course_offering o
            JOIN course c ON c.id = o.course_id
            JOIN semester s ON s.id = o.semester_id
            JOIN teacher t ON t.id = o.teacher_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAcademicCatalogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Semester> findSemesters(String status) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", status);
        return jdbcTemplate.query("""
                SELECT id, code, name, starts_on, ends_on,
                       enrollment_starts_at, enrollment_ends_at, status
                FROM semester
                WHERE (:status IS NULL OR status = :status)
                ORDER BY starts_on DESC, id DESC
                """, parameters, (resultSet, rowNumber) -> new Semester(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getObject("starts_on", java.time.LocalDate.class),
                resultSet.getObject("ends_on", java.time.LocalDate.class),
                resultSet.getObject("enrollment_starts_at", java.time.LocalDateTime.class),
                resultSet.getObject("enrollment_ends_at", java.time.LocalDateTime.class),
                resultSet.getString("status")));
    }

    @Override
    public Optional<Teacher> findTeacher(long teacherId) {
        List<Teacher> matches = jdbcTemplate.query("""
                SELECT id, teacher_no, name, department_id
                FROM teacher
                WHERE id = :teacherId
                """, Map.of("teacherId", teacherId), (resultSet, rowNumber) -> {
            long departmentId = resultSet.getLong("department_id");
            return new Teacher(
                    resultSet.getLong("id"),
                    resultSet.getString("teacher_no"),
                    resultSet.getString("name"),
                    resultSet.wasNull() ? null : departmentId);
        });
        return matches.stream().findFirst();
    }

    @Override
    public List<CourseOffering> findOfferings(long courseId, Long semesterId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("courseId", courseId);
        parameters.put("semesterId", semesterId);
        return jdbcTemplate.query(
                OFFERING_QUERY + """
                        WHERE o.course_id = :courseId
                          AND (:semesterId IS NULL OR o.semester_id = :semesterId)
                          AND o.status <> 'CANCELLED'
                        ORDER BY s.starts_on DESC, o.section_no, o.id
                        """,
                parameters,
                OFFERING_ROW_MAPPER);
    }

    @Override
    public Optional<CourseOffering> findOffering(long offeringId) {
        List<CourseOffering> matches = jdbcTemplate.query(
                OFFERING_QUERY + " WHERE o.id = :offeringId",
                Map.of("offeringId", offeringId),
                OFFERING_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    @Override
    public List<CourseSchedule> findSchedules(long offeringId) {
        return jdbcTemplate.query("""
                SELECT id, day_of_week, start_section, end_section,
                       location, start_week, end_week
                FROM course_schedule
                WHERE offering_id = :offeringId
                ORDER BY day_of_week, start_section, start_week, id
                """, Map.of("offeringId", offeringId), (resultSet, rowNumber) -> new CourseSchedule(
                resultSet.getLong("id"),
                resultSet.getInt("day_of_week"),
                resultSet.getInt("start_section"),
                resultSet.getInt("end_section"),
                resultSet.getString("location"),
                resultSet.getInt("start_week"),
                resultSet.getInt("end_week")));
    }

    private static final RowMapper<CourseOffering> OFFERING_ROW_MAPPER =
            JdbcAcademicCatalogRepository::mapOffering;

    private static CourseOffering mapOffering(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CourseOffering(
                resultSet.getLong("id"),
                resultSet.getLong("course_id"),
                resultSet.getString("course_code"),
                resultSet.getString("course_name"),
                resultSet.getLong("semester_id"),
                resultSet.getString("semester_name"),
                resultSet.getLong("teacher_id"),
                resultSet.getString("teacher_name"),
                resultSet.getString("section_no"),
                resultSet.getInt("capacity"),
                resultSet.getInt("selected_count"),
                resultSet.getString("status"));
    }
}
