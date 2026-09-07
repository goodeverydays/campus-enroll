package com.campusenroll.gatewayservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAudienceValidatorTest {

    private final JwtAudienceValidator validator = new JwtAudienceValidator("campus-enroll-api");

    @Test
    void TestValidateRequiredAudienceReturnsSuccess() {
        assertThat(validator.validate(jwt(List.of("campus-enroll-api"))).hasErrors()).isFalse();
    }

    @Test
    void TestValidateMissingAudienceReturnsFailure() {
        assertThat(validator.validate(jwt(List.of("another-api"))).hasErrors()).isTrue();
    }

    private static Jwt jwt(List<String> audiences) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("42")
                .audience(audiences)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
