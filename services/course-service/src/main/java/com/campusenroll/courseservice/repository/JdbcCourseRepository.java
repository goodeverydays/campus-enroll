package com.campusenroll.courseservice.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.courseservice.domain.Course;
import com.campusenroll.courseservice.domain.CourseCapacity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCourseRepository implements CourseRepository {

    private static final String COURSE_COLUMNS = """
            SELECT c.id, c.code, c.name, c.credits, c.total_hours, c.department_id, c.status
            FROM course c
            """;

    private static final String FILTER = """
            WHERE c.status = 'ACTIVE'
              AND (:keyword IS NULL
                   OR LOWER(c.code) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(c.name) LIKE CONCAT('%', LOWER(:keyword), '%'))
              AND (:semesterId IS NULL OR EXISTS (
                    SELECT 1
                    FROM course_offering o
                    WHERE o.course_id = c.id
                      AND o.semester_id = :semesterId
                      AND o.status IN ('PLANNED', 'OPEN')
              ))
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCourseRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CoursePage findAll(String keyword, Long semesterId, int page, int size) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", keyword);
        parameters.put("semesterId", semesterId);
        parameters.put("limit", size);
        parameters.put("offset", (long) page * size);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course c " + FILTER,
                parameters,
                Long.class);
        List<Course> items = jdbcTemplate.query(
                COURSE_COLUMNS + FILTER + " ORDER BY c.code, c.id LIMIT :limit OFFSET :offset",
                parameters,
                COURSE_ROW_MAPPER);
        return new CoursePage(items, total == null ? 0 : total);
    }

    @Override
    public Optional<Course> findById(long courseId) {
        List<Course> matches = jdbcTemplate.query(
                COURSE_COLUMNS + " WHERE c.id = :courseId",
                Map.of("courseId", courseId),
                COURSE_ROW_MAPPER);
        return matches.stream().findFirst();
    }

    @Override
    public Optional<CourseCapacity> findCapacity(long courseId, long semesterId) {
        String sql = """
                SELECT c.id AS course_id,
                       :semesterId AS semester_id,
                       COALESCE(SUM(o.capacity), 0) AS capacity,
                       COALESCE(SUM(o.selected_count), 0) AS selected_count
                FROM course c
                LEFT JOIN course_offering o
                  ON o.course_id = c.id
                 AND o.semester_id = :semesterId
                 AND o.status IN ('PLANNED', 'OPEN', 'CLOSED')
                WHERE c.id = :courseId
                GROUP BY c.id
                """;
        List<CourseCapacity> matches = jdbcTemplate.query(
                sql,
                Map.of("courseId", courseId, "semesterId", semesterId),
                (rs, rowNumber) -> new CourseCapacity(
                        rs.getLong("course_id"),
                        rs.getLong("semester_id"),
                        rs.getInt("capacity"),
                        rs.getInt("selected_count")));
        return matches.stream().findFirst();
    }

    private static final RowMapper<Course> COURSE_ROW_MAPPER = JdbcCourseRepository::mapCourse;

    private static Course mapCourse(ResultSet resultSet, int rowNumber) throws SQLException {
        long departmentId = resultSet.getLong("department_id");
        return new Course(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getBigDecimal("credits"),
                resultSet.getInt("total_hours"),
                resultSet.wasNull() ? null : departmentId,
                resultSet.getString("status"));
    }
}
