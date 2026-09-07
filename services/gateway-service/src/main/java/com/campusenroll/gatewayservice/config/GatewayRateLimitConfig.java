package com.campusenroll.gatewayservice.config;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRateLimitConfig {

    @Bean
    KeyResolver enrollmentKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> "student:"
                        + authentication.getToken().getClaims().get("student_id"))
                .switchIfEmpty(Mono.fromSupplier(() -> remoteAddressKey(exchange.getRequest().getRemoteAddress())));
    }

    private static String remoteAddressKey(InetSocketAddress address) {
        return address == null ? "remote:unknown" : "remote:" + address.getAddress().getHostAddress();
    }
}
