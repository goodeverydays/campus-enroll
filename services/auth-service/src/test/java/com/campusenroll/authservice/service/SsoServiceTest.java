package com.campusenroll.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.campusenroll.authservice.api.TicketExchangeRequest;
import com.campusenroll.authservice.api.TicketIssueRequest;
import com.campusenroll.authservice.api.TokenResponse;
import com.campusenroll.authservice.config.SecurityProperties;
import com.campusenroll.authservice.domain.LegacyIdentity;
import com.campusenroll.authservice.domain.TicketPrincipal;
import com.campusenroll.authservice.repository.AuthRepository;
import com.campusenroll.authservice.support.AuthConflictException;
import com.campusenroll.authservice.support.AuthUnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsoServiceTest {

    @Mock
    private AuthRepository authRepository;
    @Mock
    private LegacySystemKeyVerifier keyVerifier;
    @Mock
    private JwtTokenService jwtTokenService;

    private SsoService ssoService;

    @BeforeEach
    void setUp() {
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "unused", "https://campus-enroll.local", "campus-enroll-api", 900),
                new SecurityProperties.Sso(120, "unused"));
        ssoService = new SsoService(authRepository, keyVerifier, jwtTokenService, properties);
    }

    @Test
    void TestIssueTicketNewIdentityStoresOnlyTicketHash() {
        var request = new TicketIssueRequest("legacy-sis", "user-42", 42L);
        when(authRepository.findIdentity("legacy-sis", "user-42")).thenReturn(Optional.empty());
        when(authRepository.findIdentityByStudentId(42L)).thenReturn(Optional.empty());
        when(authRepository.createIdentity("legacy-sis", "user-42", 42L))
                .thenReturn(new LegacyIdentity(7L, "legacy-sis", "user-42", 42L));

        var response = ssoService.issueTicket("system-key", request);

        verify(keyVerifier).verify("system-key");
        var hash = ArgumentCaptor.forClass(String.class);
        verify(authRepository).createTicket(eq(7L), hash.capture(), eq(120L));
        assertThat(response.ticket()).hasSize(43).doesNotContain(hash.getValue());
        assertThat(hash.getValue()).matches("[0-9a-f]{64}");
        assertThat(response.expiresIn()).isEqualTo(120L);
    }

    @Test
    void TestIssueTicketExistingStudentMappedElsewhereReturnsConflict() {
        var request = new TicketIssueRequest("legacy-sis", "user-42", 42L);
        when(authRepository.findIdentity("legacy-sis", "user-42")).thenReturn(Optional.empty());
        when(authRepository.findIdentityByStudentId(42L))
                .thenReturn(Optional.of(new LegacyIdentity(8L, "other-system", "other-user", 42L)));

        assertThatThrownBy(() -> ssoService.issueTicket("system-key", request))
                .isInstanceOf(AuthConflictException.class);
    }

    @Test
    void TestExchangeConsumedOrUnknownTicketReturnsUnauthorized() {
        when(authRepository.consumeTicket(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ssoService.exchange(new TicketExchangeRequest("unknown-ticket")))
                .isInstanceOf(AuthUnauthorizedException.class)
                .hasMessageContaining("invalid, expired, or consumed");
    }

    @Test
    void TestExchangeValidTicketReturnsIssuedJwt() {
        var principal = new TicketPrincipal("legacy-sis", "user-42", 42L);
        var token = new TokenResponse("Bearer", "signed.jwt.token", 900L);
        when(authRepository.consumeTicket(anyString())).thenReturn(Optional.of(principal));
        when(jwtTokenService.issue(principal)).thenReturn(token);

        assertThat(ssoService.exchange(new TicketExchangeRequest("one-time-ticket"))).isEqualTo(token);
    }
}
