package com.campusenroll.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.campusenroll.authservice.config.SecurityProperties;
import com.campusenroll.authservice.domain.TicketPrincipal;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {

    @Test
    void TestIssueJwtCarriesExpectedTrustClaimsAndHs256Signature() {
        byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        Base64.getEncoder().encodeToString(secret),
                        "https://campus-enroll.local",
                        "campus-enroll-api",
                        900),
                new SecurityProperties.Sso(120, "unused"));
        var service = new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(secret)), properties);
        var response = service.issue(new TicketPrincipal("legacy-sis", "user-42", 42L));

        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var jwt = decoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://campus-enroll.local");
        assertThat(jwt.getAudience()).containsExactly("campus-enroll-api");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(((Number) jwt.getClaim("student_id")).longValue()).isEqualTo(42L);
        assertThat(jwt.getClaimAsString("legacy_system")).isEqualTo("legacy-sis");
    }
}
