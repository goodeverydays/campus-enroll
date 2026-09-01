package com.campusenroll.gatewayservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class TrustedStudentHeaderFilterTest {

    private final TrustedStudentHeaderFilter filter = new TrustedStudentHeaderFilter();

    @Test
    void TestFilterAuthenticatedJwtReplacesForgedStudentHeader() {
        var original = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/students/me")
                .header(TrustedStudentHeaderFilter.STUDENT_ID_HEADER, "999"));
        var exchange = original.mutate()
                .principal(Mono.just(new JwtAuthenticationToken(jwt(42L))))
                .build();
        var forwarded = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = candidate -> {
            forwarded.set(candidate);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(TrustedStudentHeaderFilter.STUDENT_ID_HEADER)).isEqualTo("42");
    }

    @Test
    void TestFilterAnonymousRequestRemovesForgedStudentHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/courses")
                .header(TrustedStudentHeaderFilter.STUDENT_ID_HEADER, "999"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = candidate -> {
            forwarded.set(candidate);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .containsKey(TrustedStudentHeaderFilter.STUDENT_ID_HEADER)).isFalse();
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
