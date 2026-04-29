package com.fan.lazyday.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleAuthorizationFilterTest {

    private RoleAuthorizationFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RoleAuthorizationFilter();
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("角色匹配时只执行一次下游链")
    void matchingRole_shouldInvokeChainOnce() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota").build()
        );

        StepVerifier.create(
                        filter.filter(exchange, chain)
                                .contextWrite(TenantContext.write(1L, 100L, "TENANT_ADMIN"))
                )
                .verifyComplete();

        verify(chain).filter(exchange);
    }
}
