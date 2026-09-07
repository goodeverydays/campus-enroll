package com.campusenroll.gatewayservice.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TrustedStudentHeaderFilter implements GlobalFilter, Ordered {

    public static final String STUDENT_ID_HEADER = "X-Student-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate()
                .request(request -> request.headers(headers -> headers.remove(STUDENT_ID_HEADER)))
                .build();
        return sanitized.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> {
                    Object studentId = authentication.getToken().getClaims().get("student_id");
                    if (studentId == null) {
                        return sanitized;
                    }
                    return sanitized.mutate()
                            .request(request -> request.headers(headers ->
                                    headers.set(STUDENT_ID_HEADER, studentId.toString())))
                            .build();
                })
                .defaultIfEmpty(sanitized)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
