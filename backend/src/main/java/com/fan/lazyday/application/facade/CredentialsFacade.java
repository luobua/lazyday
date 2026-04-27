package com.fan.lazyday.application.facade;

import com.fan.lazyday.interfaces.response.AppKeyResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface CredentialsFacade {
    Mono<List<AppKeyResponse>> list(Long tenantId);
    Mono<AppKeyResponse> create(Long tenantId, String name, String scopes);
    Mono<Void> disable(Long tenantId, Long id);
    Mono<Void> enable(Long tenantId, Long id);
    Mono<AppKeyResponse> rotateSecret(Long tenantId, Long id);
    Mono<Void> delete(Long tenantId, Long id);
}
