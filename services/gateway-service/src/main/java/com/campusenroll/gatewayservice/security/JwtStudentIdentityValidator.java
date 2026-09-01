package com.campusenroll.gatewayservice.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtStudentIdentityValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_STUDENT_IDENTITY = new OAuth2Error(
            "invalid_token", "Student identity claim is missing or inconsistent", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object claim = token.getClaim("student_id");
        if (!(claim instanceof Number studentId) || studentId.longValue() <= 0) {
            return OAuth2TokenValidatorResult.failure(INVALID_STUDENT_IDENTITY);
        }
        return Long.toString(studentId.longValue()).equals(token.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_STUDENT_IDENTITY);
    }
}
