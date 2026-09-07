package com.campusenroll.authservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.campusenroll.authservice.api.TokenResponse;
import com.campusenroll.authservice.config.SecurityProperties;
import com.campusenroll.authservice.domain.TicketPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public TokenResponse issue(TicketPrincipal principal) {
        Instant issuedAt = Instant.now();
        long ttlSeconds = properties.jwt().ttlSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(Long.toString(principal.studentId()))
                .audience(List.of(properties.jwt().audience()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(ttlSeconds))
                .id(UUID.randomUUID().toString())
                .claim("student_id", principal.studentId())
                .claim("legacy_system", principal.legacySystem())
                .claim("scope", "student")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse("Bearer", token, ttlSeconds);
    }
}
