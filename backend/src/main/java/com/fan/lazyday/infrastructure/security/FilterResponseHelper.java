package com.fan.lazyday.infrastructure.security;

import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

final class FilterResponseHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FilterResponseHelper() {
    }

    static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .flatMap(requestId -> {
                    ApiResponse<Void> response = ApiResponse.error(
                            status.value() * 100 + 1, errorCode, message, requestId);

                    ServerHttpResponse httpResponse = exchange.getResponse();
                    httpResponse.setStatusCode(status);
                    httpResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    // Add CORS headers so the browser can read the error response
                    addCorsHeaders(exchange);

                    try {
                        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(response);
                        DataBuffer buffer = httpResponse.bufferFactory().wrap(bytes);
                        return httpResponse.writeWith(Mono.just(buffer));
                    } catch (Exception e) {
                        byte[] fallback = ("{\"error_code\":\"" + errorCode + "\"}").getBytes(StandardCharsets.UTF_8);
                        DataBuffer buffer = httpResponse.bufferFactory().wrap(fallback);
                        return httpResponse.writeWith(Mono.just(buffer));
                    }
                });
    }

    /**
     * Manually add CORS headers when a WebFilter short-circuits the response
     * before the CorsWebFilter has a chance to process it.
     */
    private static void addCorsHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String origin = request.getHeaders().getOrigin();
        ServerHttpResponse response = exchange.getResponse();

        if (origin != null && !origin.isBlank()) {
            response.getHeaders().add("Access-Control-Allow-Origin", origin);
            response.getHeaders().add("Access-Control-Allow-Credentials", "true");
            response.getHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,PATCH,OPTIONS");
            response.getHeaders().add("Access-Control-Allow-Headers", "Content-Type,X-CSRF-Token,X-Request-Id,Authorization");
            response.getHeaders().add("Access-Control-Expose-Headers", "X-Request-Id");
        }
    }
}
