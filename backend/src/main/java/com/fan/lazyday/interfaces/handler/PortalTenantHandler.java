package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.infrastructure.config.path.RequestMappingPortalV1;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.PortalTenantApi;
import com.fan.lazyday.interfaces.request.UpdateTenantRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.query.Update;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMappingPortalV1
@RequiredArgsConstructor
public class PortalTenantHandler implements PortalTenantApi {

    private final TenantRepository tenantRepository;

    @Override
    public Mono<ApiResponse<TenantResponse>> getTenant() {
        return TenantContext.current()
                .flatMap(ctx -> tenantRepository.findById(ctx.getTenantId()))
                .switchIfEmpty(Mono.error(BizException.notFound("TENANT_NOT_FOUND", "租户不存在")))
                .map(this::toResponse)
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<TenantResponse>> updateTenant(Mono<UpdateTenantRequest> request) {
        return TenantContext.current()
                .flatMap(ctx -> request.flatMap(req -> {
                    Update update = Update.update("name", req.getName())
                            .set("contact_email", req.getContactEmail());
                    return tenantRepository.update(ctx.getTenantId(), update)
                            .then(tenantRepository.findById(ctx.getTenantId()));
                }))
                .map(this::toResponse)
                .flatMap(this::wrapSuccess);
    }

    private TenantResponse toResponse(Tenant tenant) {
        TenantResponse response = new TenantResponse();
        response.setId(tenant.getId());
        response.setName(tenant.getName());
        response.setStatus(tenant.getStatus());
        response.setPlanType(tenant.getPlanType());
        response.setContactEmail(tenant.getContactEmail());
        return response;
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
