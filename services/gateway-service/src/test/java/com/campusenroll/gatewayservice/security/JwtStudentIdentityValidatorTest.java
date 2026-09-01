package com.campusenroll.gatewayservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtStudentIdentityValidatorTest {

    private final JwtStudentIdentityValidator validator = new JwtStudentIdentityValidator();

    @Test
    void TestValidateMatchingPositiveStudentIdentityReturnsSuccess() {
        assertThat(validator.validate(jwt("42", 42L)).hasErrors()).isFalse();
    }

    @Test
    void TestValidateMismatchedStudentIdentityReturnsFailure() {
        assertThat(validator.validate(jwt("41", 42L)).hasErrors()).isTrue();
    }

    @Test
    void TestValidateMissingStudentIdentityReturnsFailure() {
        assertThat(validator.validate(jwt("42", null)).hasErrors()).isTrue();
    }

    private static Jwt jwt(String subject, Long studentId) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if (studentId != null) {
            builder.claim("student_id", studentId);
        }
        return builder.build();
    }
}
