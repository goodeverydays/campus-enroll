package com.campusenroll.gatewayservice.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdGlobalFilterTest {

    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter();

    @Test
    void TestFilterSafeRequestIdIsForwardedAndReturned() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/courses")
                .header(RequestIdGlobalFilter.REQUEST_ID_HEADER, "frontend:request-42"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = candidate -> {
            forwarded.set(candidate);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER)).isEqualTo("frontend:request-42");
        assertThat(forwarded.get().getResponse().getHeaders()
                .getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER)).isEqualTo("frontend:request-42");
    }

    @Test
    void TestFilterUnsafeRequestIdIsReplaced() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/courses")
                .header(RequestIdGlobalFilter.REQUEST_ID_HEADER, "contains spaces and is unsafe"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = candidate -> {
            forwarded.set(candidate);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String requestId = forwarded.get().getRequest().getHeaders()
                .getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER);
        assertThat(requestId)
                .isNotEqualTo("contains spaces and is unsafe")
                .matches("[0-9a-f-]{36}");
        assertThat(forwarded.get().getResponse().getHeaders()
                .getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER)).isEqualTo(requestId);
    }
}
