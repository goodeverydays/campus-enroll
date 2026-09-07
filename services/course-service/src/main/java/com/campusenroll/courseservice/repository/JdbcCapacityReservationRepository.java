package com.campusenroll.courseservice.repository;

import java.util.Map;
import java.util.Optional;

import com.campusenroll.courseservice.domain.CapacityReservation;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCapacityReservationRepository implements CapacityReservationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCapacityReservationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void ensure(String requestId, long offeringId) {
        jdbcTemplate.update("""
                INSERT INTO course_capacity_reservation (request_id, offering_id, status)
                VALUES (:requestId, :offeringId, 'RELEASED')
                ON DUPLICATE KEY UPDATE request_id = VALUES(request_id)
                """, Map.of("requestId", requestId, "offeringId", offeringId));
    }

    @Override
    public Optional<CapacityReservation> lock(String requestId) {
        return jdbcTemplate.query("""
                SELECT request_id, offering_id, status
                FROM course_capacity_reservation
                WHERE request_id = :requestId
                FOR UPDATE
                """, Map.of("requestId", requestId), (resultSet, rowNumber) -> new CapacityReservation(
                resultSet.getString("request_id"),
                resultSet.getLong("offering_id"),
                resultSet.getString("status"))).stream().findFirst();
    }

    @Override
    public void markReserved(String requestId) {
        updateStatus(requestId, "RESERVED");
    }

    @Override
    public void markReleased(String requestId) {
        updateStatus(requestId, "RELEASED");
    }

    private void updateStatus(String requestId, String status) {
        int updated = jdbcTemplate.update("""
                UPDATE course_capacity_reservation
                SET status = :status
                WHERE request_id = :requestId
                """, Map.of("requestId", requestId, "status", status));
        if (updated != 1) {
            throw new IllegalStateException("Capacity reservation record disappeared");
        }
    }
}
