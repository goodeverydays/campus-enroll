package com.campusenroll.studentservice.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.studentservice.domain.StudentProfile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStudentRepository implements StudentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStudentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StudentProfile> findById(long studentId) {
        String sql = """
                SELECT s.id,
                       s.student_no,
                       s.name,
                       s.department_id,
                       d.name AS department_name,
                       s.major_id,
                       m.name AS major_name,
                       s.grade_year,
                       s.status
                FROM student s
                JOIN department d ON d.id = s.department_id
                JOIN major m ON m.id = s.major_id
                WHERE s.id = :studentId
                """;
        List<StudentProfile> matches = jdbcTemplate.query(
                sql,
                Map.of("studentId", studentId),
                (resultSet, rowNumber) -> new StudentProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("student_no"),
                        resultSet.getString("name"),
                        resultSet.getLong("department_id"),
                        resultSet.getString("department_name"),
                        resultSet.getLong("major_id"),
                        resultSet.getString("major_name"),
                        resultSet.getInt("grade_year"),
                        resultSet.getString("status")));
        return matches.stream().findFirst();
    }
}
