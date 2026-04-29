package com.fan.lazyday.infrastructure.security;

import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    @Mock
    private WebFilterChain chain;

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.setInternalApiKey("test-internal-key");
        filter = new InternalApiKeyFilter(serviceProperties);
    }

    @Test
    @DisplayName("非 /internal/** 路径 -> 直接放行")
    void nonInternalPath_shouldPassThrough() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/portal/v1/quota").build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("internal 路径携带正确 api key -> 放行")
    void internalPath_withValidApiKey_shouldPassThrough() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/quota/effective")
                        .header("X-Internal-Api-Key", "test-internal-key")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("internal 路径缺失 api key -> 返回 403 INTERNAL_AUTH_FAILED")
    void internalPath_withoutApiKey_shouldReturn403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/quota/effective").build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("internal 路径 api key 错误 -> 返回 403 INTERNAL_AUTH_FAILED")
    void internalPath_withInvalidApiKey_shouldReturn403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/quota/effective")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(chain);
    }
}
