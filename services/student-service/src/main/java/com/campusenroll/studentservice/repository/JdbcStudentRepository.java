package com.campusenroll.studentservice.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.studentservice.domain.StudentProfile;
import com.campusenroll.studentservice.domain.LegacyStudentSyncCommand;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStudentRepository implements StudentRepository {

    private static final String PROFILE_QUERY = """
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
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStudentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StudentProfile> findById(long studentId) {
        return findOne(PROFILE_QUERY + " WHERE s.id = :value", Map.of("value", studentId));
    }

    @Override
    public Optional<StudentProfile> findByLegacyStudentId(String legacyStudentId) {
        return findOne(
                PROFILE_QUERY + " WHERE s.legacy_student_id = :value",
                Map.of("value", legacyStudentId));
    }

    @Override
    public Optional<StudentProfile> findByStudentNo(String studentNo) {
        return findOne(PROFILE_QUERY + " WHERE s.student_no = :value", Map.of("value", studentNo));
    }

    @Override
    public StudentProfile synchronize(LegacyStudentSyncCommand command, Long existingStudentId) {
        Map<String, Object> departmentParameters = Map.of(
                "code", command.departmentCode(),
                "name", command.departmentName());
        jdbcTemplate.update("""
                INSERT INTO department (code, name)
                VALUES (:code, :name)
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, departmentParameters);
        Long departmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE code = :code",
                departmentParameters,
                Long.class);

        Map<String, Object> majorParameters = Map.of(
                "departmentId", departmentId,
                "code", command.majorCode(),
                "name", command.majorName());
        jdbcTemplate.update("""
                INSERT INTO major (department_id, code, name)
                VALUES (:departmentId, :code, :name)
                ON DUPLICATE KEY UPDATE
                    department_id = VALUES(department_id),
                    name = VALUES(name)
                """, majorParameters);
        Long majorId = jdbcTemplate.queryForObject(
                "SELECT id FROM major WHERE code = :code",
                majorParameters,
                Long.class);

        Map<String, Object> studentParameters = new java.util.HashMap<>();
        studentParameters.put("id", existingStudentId);
        studentParameters.put("legacyStudentId", command.legacyStudentId());
        studentParameters.put("studentNo", command.studentNo());
        studentParameters.put("name", command.name());
        studentParameters.put("departmentId", departmentId);
        studentParameters.put("majorId", majorId);
        studentParameters.put("gradeYear", command.gradeYear());
        studentParameters.put("status", command.status());
        if (existingStudentId == null) {
            jdbcTemplate.update("""
                    INSERT INTO student (
                        legacy_student_id, student_no, name, department_id, major_id, grade_year, status)
                    VALUES (
                        :legacyStudentId, :studentNo, :name, :departmentId, :majorId, :gradeYear, :status)
                    """, studentParameters);
        } else {
            jdbcTemplate.update("""
                    UPDATE student
                    SET legacy_student_id = :legacyStudentId,
                        student_no = :studentNo,
                        name = :name,
                        department_id = :departmentId,
                        major_id = :majorId,
                        grade_year = :gradeYear,
                        status = :status,
                        version = version + 1
                    WHERE id = :id
                    """, studentParameters);
        }
        return findByLegacyStudentId(command.legacyStudentId())
                .orElseThrow(() -> new IllegalStateException("Synchronized student could not be reloaded"));
    }

    private Optional<StudentProfile> findOne(String sql, Map<String, ?> parameters) {
        List<StudentProfile> matches = jdbcTemplate.query(
                sql,
                parameters,
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
