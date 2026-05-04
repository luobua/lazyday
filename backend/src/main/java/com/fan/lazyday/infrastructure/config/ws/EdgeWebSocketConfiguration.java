package com.fan.lazyday.infrastructure.config.ws;

import com.fan.lazyday.infrastructure.ws.EdgeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class EdgeWebSocketConfiguration {

    private final EdgeWebSocketHandler edgeWebSocketHandler;

    @Bean
    public HandlerMapping edgeWebSocketHandlerMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/edge", edgeWebSocketHandler));
        mapping.setOrder(-1);
        return mapping;
    }
}
