package com.campusenroll.gatewayservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitConfigTest {

    private final GatewayRateLimitConfig config = new GatewayRateLimitConfig();

    @Test
    void TestEnrollmentKeyResolverAuthenticatedStudentUsesStableIdentity() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/enrollments"));
        exchange = exchange.mutate().principal(Mono.just(new JwtAuthenticationToken(jwt(42L)))).build();

        String key = config.enrollmentKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("student:42");
    }

    @Test
    void TestEnrollmentKeyResolverAnonymousRequestUsesRemoteAddress() {
        var request = MockServerHttpRequest.post("/api/v1/enrollments")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 43120))
                .build();
        var exchange = MockServerWebExchange.from(request);

        String key = config.enrollmentKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("remote:192.0.2.10");
    }

    private static Jwt jwt(long studentId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(Long.toString(studentId))
                .claim("student_id", studentId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
