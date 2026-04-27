package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.CredentialsFacade;
import com.fan.lazyday.domain.appkey.entity.AppKeyEntity;
import com.fan.lazyday.domain.appkey.po.AppKey;
import com.fan.lazyday.domain.appkey.repository.AppKeyRepository;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.interfaces.response.AppKeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CredentialsFacadeImpl implements CredentialsFacade {

    private final AppKeyRepository appKeyRepository;
    private final ServiceProperties serviceProperties;

    @Override
    public Mono<List<AppKeyResponse>> list(Long tenantId) {
        return appKeyRepository.findByTenantId(tenantId)
                .map(this::toMaskedResponse)
                .collectList();
    }

    @Override
    public Mono<AppKeyResponse> create(Long tenantId, String name, String scopes) {
        String appKeyValue = AppKeyEntity.generateAppKey();
        String secretKeyValue = AppKeyEntity.generateSecretKey();
        String encryptedSecret = AppKeyEntity.encryptSecretKey(secretKeyValue, serviceProperties.getEncryptionKey());

        AppKey po = new AppKey();
        po.setTenantId(tenantId);
        po.setName(name);
        po.setAppKey(appKeyValue);
        po.setSecretKeyEncrypted(encryptedSecret);
        po.setStatus("ACTIVE");
        po.setScopes(scopes);

        return appKeyRepository.insert(po)
                .map(saved -> {
                    AppKeyResponse response = toMaskedResponse(saved);
                    response.setAppKey(appKeyValue);
                    response.setSecretKey(secretKeyValue);
                    return response;
                });
    }

    @Override
    public Mono<Void> disable(Long tenantId, Long id) {
        return appKeyRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(BizException.forbidden("FORBIDDEN_TENANT", "无权操作此资源")))
                .flatMap(appKey -> appKeyRepository.updateByIdAndTenantId(id, tenantId,
                        Update.update("status", "DISABLED")))
                .then();
    }

    @Override
    public Mono<Void> enable(Long tenantId, Long id) {
        return appKeyRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(BizException.forbidden("FORBIDDEN_TENANT", "无权操作此资源")))
                .flatMap(appKey -> appKeyRepository.updateByIdAndTenantId(id, tenantId,
                        Update.update("status", "ACTIVE")))
                .then();
    }

    @Override
    public Mono<AppKeyResponse> rotateSecret(Long tenantId, Long id) {
        return appKeyRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(BizException.forbidden("FORBIDDEN_TENANT", "无权操作此资源")))
                .flatMap(appKey -> {
                    AppKeyEntity entity = AppKeyEntity.fromPo(appKey);
                    String newSecretKey = entity.rotateSecretKey(serviceProperties.getEncryptionKey());

                    Update update = Update.update("secret_key_encrypted", appKey.getSecretKeyEncrypted())
                            .set("secret_key_old", appKey.getSecretKeyOld())
                            .set("rotated_at", appKey.getRotatedAt())
                            .set("grace_period_end", appKey.getGracePeriodEnd());

                    return appKeyRepository.updateByIdAndTenantId(id, tenantId, update)
                            .thenReturn(newSecretKey)
                            .map(sk -> {
                                AppKeyResponse response = toMaskedResponse(appKey);
                                response.setSecretKey(sk);
                                return response;
                            });
                });
    }

    @Override
    public Mono<Void> delete(Long tenantId, Long id) {
        return appKeyRepository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(BizException.forbidden("FORBIDDEN_TENANT", "无权操作此资源")))
                .flatMap(appKey -> appKeyRepository.deleteByIdAndTenantId(id, tenantId))
                .then();
    }

    private AppKeyResponse toMaskedResponse(AppKey appKey) {
        AppKeyResponse response = new AppKeyResponse();
        response.setId(appKey.getId());
        response.setName(appKey.getName());
        response.setAppKey(maskKey(appKey.getAppKey()));
        response.setStatus(appKey.getStatus());
        response.setScopes(appKey.getScopes());
        response.setCreateTime(appKey.getCreateTime());
        return response;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return key;
        return key.substring(0, 7) + "****" + key.substring(key.length() - 4);
    }
}
