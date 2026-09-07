package com.campusenroll.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import com.campusenroll.authservice.api.TicketExchangeRequest;
import com.campusenroll.authservice.api.TicketIssueRequest;
import com.campusenroll.authservice.api.TicketIssueResponse;
import com.campusenroll.authservice.api.TokenResponse;
import com.campusenroll.authservice.config.SecurityProperties;
import com.campusenroll.authservice.domain.LegacyIdentity;
import com.campusenroll.authservice.domain.TicketPrincipal;
import com.campusenroll.authservice.repository.AuthRepository;
import com.campusenroll.authservice.support.AuthConflictException;
import com.campusenroll.authservice.support.AuthUnauthorizedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SsoService {

    private final AuthRepository authRepository;
    private final LegacySystemKeyVerifier keyVerifier;
    private final JwtTokenService jwtTokenService;
    private final SecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SsoService(
            AuthRepository authRepository,
            LegacySystemKeyVerifier keyVerifier,
            JwtTokenService jwtTokenService,
            SecurityProperties properties) {
        this.authRepository = authRepository;
        this.keyVerifier = keyVerifier;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
    }

    @Transactional
    public TicketIssueResponse issueTicket(String suppliedSystemKey, TicketIssueRequest request) {
        keyVerifier.verify(suppliedSystemKey);
        String legacySystem = request.legacySystem().trim();
        String legacyUserId = request.legacyUserId().trim();
        Optional<LegacyIdentity> byLegacy = authRepository.findIdentity(legacySystem, legacyUserId);
        Optional<LegacyIdentity> byStudent = authRepository.findIdentityByStudentId(request.studentId());
        if (byLegacy.isPresent() && byLegacy.get().studentId() != request.studentId()) {
            throw new AuthConflictException("Legacy identity is already mapped to another student");
        }
        if (byStudent.isPresent()
                && (!byStudent.get().legacySystem().equals(legacySystem)
                    || !byStudent.get().legacyUserId().equals(legacyUserId))) {
            throw new AuthConflictException("Student is already mapped to another legacy identity");
        }

        LegacyIdentity identity = byLegacy
                .or(() -> byStudent)
                .orElseGet(() -> createIdentity(legacySystem, legacyUserId, request.studentId()));
        String ticket = randomTicket();
        authRepository.createTicket(identity.id(), sha256(ticket), properties.sso().ticketTtlSeconds());
        return new TicketIssueResponse(ticket, properties.sso().ticketTtlSeconds());
    }

    @Transactional
    public TokenResponse exchange(TicketExchangeRequest request) {
        TicketPrincipal principal = authRepository.consumeTicket(sha256(request.ticket()))
                .orElseThrow(() -> new AuthUnauthorizedException(40101, "Ticket is invalid, expired, or consumed"));
        return jwtTokenService.issue(principal);
    }

    private LegacyIdentity createIdentity(String legacySystem, String legacyUserId, long studentId) {
        try {
            return authRepository.createIdentity(legacySystem, legacyUserId, studentId);
        } catch (DuplicateKeyException exception) {
            throw new AuthConflictException("Legacy identity mapping changed concurrently");
        }
    }

    private String randomTicket() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
