package com.campusenroll.authservice.repository;

import java.util.Optional;

import com.campusenroll.authservice.domain.LegacyIdentity;
import com.campusenroll.authservice.domain.TicketPrincipal;

public interface AuthRepository {

    Optional<LegacyIdentity> findIdentity(String legacySystem, String legacyUserId);

    Optional<LegacyIdentity> findIdentityByStudentId(long studentId);

    LegacyIdentity createIdentity(String legacySystem, String legacyUserId, long studentId);

    void createTicket(long legacyIdentityId, String ticketHash, long ttlSeconds);

    Optional<TicketPrincipal> consumeTicket(String ticketHash);
}
