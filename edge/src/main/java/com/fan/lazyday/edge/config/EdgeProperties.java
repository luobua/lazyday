package com.fan.lazyday.edge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "edge")
public class EdgeProperties {
    private String backendWsUrl = "ws://127.0.0.1:8080";
    private Duration heartbeatInterval = Duration.ofSeconds(15);
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofSeconds(30);
    private boolean autoStart = true;
}
