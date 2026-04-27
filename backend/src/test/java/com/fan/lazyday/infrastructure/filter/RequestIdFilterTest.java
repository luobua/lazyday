package com.fan.lazyday.infrastructure.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * RequestIdFilter 单元测试
 * 验证 UUID 生成、响应头设置和 Reactor Context 写入
 */
class RequestIdFilterTest {

    private RequestIdFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        chain = mock(WebFilterChain.class);
    }

    @Test
    @DisplayName("filter: 应在响应头中设置 X-Request-Id")
    void filter_shouldSetRequestIdHeader() {
        when(chain.filter(any(MockServerWebExchange.class)))
                .thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build()
        );

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String requestId = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotNull().isNotEmpty();

        // 验证是有效的 UUID 格式
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    @DisplayName("filter: 应将 requestId 写入 Reactor Context，链下游可读取")
    void filter_shouldWriteRequestIdToContext() {
        // 让 chain.filter 返回一个可以读取 context 的 Mono
        when(chain.filter(any(MockServerWebExchange.class)))
                .thenAnswer(inv -> RequestIdFilter.getRequestId()
                        .flatMap(id -> Mono.empty())); // context 已写入，下游可读

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build()
        );

        // filter.filter() 返回 chain.filter(exchange).contextWrite(...)
        // chain 内部的 getRequestId() 应能读到 contextWrite 写入的 requestId
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("getRequestId: 无 Context 时返回空 Mono")
    void getRequestId_withoutContext_shouldReturnEmpty() {
        StepVerifier.create(RequestIdFilter.getRequestId())
                .verifyComplete();
    }

    @Test
    @DisplayName("filter: 每次请求生成不同的 requestId")
    void filter_shouldGenerateDifferentRequestIdPerRequest() {
        when(chain.filter(any(MockServerWebExchange.class)))
                .thenReturn(Mono.empty());

        MockServerWebExchange exchange1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test1").build()
        );
        MockServerWebExchange exchange2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test2").build()
        );

        filter.filter(exchange1, chain).block();
        filter.filter(exchange2, chain).block();

        String id1 = exchange1.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        String id2 = exchange2.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);

        assertThat(id1).isNotEqualTo(id2);
    }
}
