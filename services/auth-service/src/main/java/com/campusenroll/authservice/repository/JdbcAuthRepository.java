package com.campusenroll.authservice.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusenroll.authservice.domain.LegacyIdentity;
import com.campusenroll.authservice.domain.TicketPrincipal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthRepository implements AuthRepository {

    private static final String IDENTITY_SELECT = """
            SELECT id, legacy_system, legacy_user_id, student_id
            FROM legacy_identity
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAuthRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LegacyIdentity> findIdentity(String legacySystem, String legacyUserId) {
        return findOne(
                IDENTITY_SELECT + " WHERE legacy_system = :legacySystem AND legacy_user_id = :legacyUserId",
                Map.of("legacySystem", legacySystem, "legacyUserId", legacyUserId));
    }

    @Override
    public Optional<LegacyIdentity> findIdentityByStudentId(long studentId) {
        return findOne(IDENTITY_SELECT + " WHERE student_id = :studentId", Map.of("studentId", studentId));
    }

    @Override
    public LegacyIdentity createIdentity(String legacySystem, String legacyUserId, long studentId) {
        jdbcTemplate.update("""
                INSERT INTO legacy_identity (legacy_system, legacy_user_id, student_id)
                VALUES (:legacySystem, :legacyUserId, :studentId)
                """, Map.of(
                "legacySystem", legacySystem,
                "legacyUserId", legacyUserId,
                "studentId", studentId));
        return findIdentity(legacySystem, legacyUserId)
                .orElseThrow(() -> new IllegalStateException("Created identity could not be reloaded"));
    }

    @Override
    public void createTicket(long legacyIdentityId, String ticketHash, long ttlSeconds) {
        jdbcTemplate.update("""
                INSERT INTO sso_ticket (ticket_hash, legacy_identity_id, expires_at)
                VALUES (:ticketHash, :legacyIdentityId,
                        TIMESTAMPADD(SECOND, :ttlSeconds, CURRENT_TIMESTAMP(3)))
                """, Map.of(
                "ticketHash", ticketHash,
                "legacyIdentityId", legacyIdentityId,
                "ttlSeconds", ttlSeconds));
    }

    @Override
    public Optional<TicketPrincipal> consumeTicket(String ticketHash) {
        int consumed = jdbcTemplate.update("""
                UPDATE sso_ticket
                SET consumed_at = CURRENT_TIMESTAMP(3)
                WHERE ticket_hash = :ticketHash
                  AND consumed_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP(3)
                """, Map.of("ticketHash", ticketHash));
        if (consumed != 1) {
            return Optional.empty();
        }
        List<TicketPrincipal> matches = jdbcTemplate.query("""
                SELECT i.legacy_system, i.legacy_user_id, i.student_id
                FROM sso_ticket t
                JOIN legacy_identity i ON i.id = t.legacy_identity_id
                WHERE t.ticket_hash = :ticketHash
                """, Map.of("ticketHash", ticketHash), (resultSet, rowNumber) -> new TicketPrincipal(
                resultSet.getString("legacy_system"),
                resultSet.getString("legacy_user_id"),
                resultSet.getLong("student_id")));
        return matches.stream().findFirst();
    }

    private Optional<LegacyIdentity> findOne(String sql, Map<String, ?> parameters) {
        List<LegacyIdentity> matches = jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) ->
                new LegacyIdentity(
                        resultSet.getLong("id"),
                        resultSet.getString("legacy_system"),
                        resultSet.getString("legacy_user_id"),
                        resultSet.getLong("student_id")));
        return matches.stream().findFirst();
    }
}
