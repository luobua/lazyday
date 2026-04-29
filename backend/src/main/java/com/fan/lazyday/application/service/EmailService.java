package com.fan.lazyday.application.service;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface EmailService {
    Mono<Void> send(List<String> toAddresses, String subject, String templateName, Map<String, Object> model);
}
