package com.fan.lazyday.application.facade.impl;

import com.fan.lazyday.application.facade.AdminTenantFacade;
import com.fan.lazyday.domain.appkey.repository.AppKeyRepository;
import com.fan.lazyday.domain.calllog.repository.CallLogRepository;
import com.fan.lazyday.domain.event.TenantResumedEvent;
import com.fan.lazyday.domain.event.TenantSuspendedEvent;
import com.fan.lazyday.domain.tenant.po.Tenant;
import com.fan.lazyday.domain.tenant.repository.TenantRepository;
import com.fan.lazyday.domain.user.repository.UserRepository;
import com.fan.lazyday.infrastructure.event.DomainEventPublisher;
import com.fan.lazyday.infrastructure.exception.BizException;
import com.fan.lazyday.interfaces.response.AdminOverviewMetricsResponse;
import com.fan.lazyday.interfaces.response.AdminTenantDetailResponse;
import com.fan.lazyday.interfaces.response.AdminTenantSummaryResponse;
import com.fan.lazyday.interfaces.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

@Component
@RequiredArgsConstructor
public class AdminTenantFacadeImpl implements AdminTenantFacade {

    private final TenantRepository tenantRepository;
    private final CallLogRepository callLogRepository;
    private final AppKeyRepository appKeyRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public Mono<PageResponse<AdminTenantSummaryResponse>> listTenants(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "created_time"));
        return tenantRepository.findPage(keyword, status, pageRequest)
                .map(pageResult -> PageResponse.of(
                        pageResult.getContent(),
                        pageResult.getTotalElements(),
                        pageResult.getNumber(),
                        pageResult.getSize(),
                        pageResult.getTotalPages()
                ));
    }

    @Override
    public Mono<AdminTenantDetailResponse> getTenantDetail(Long tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonthStart = today.with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(ZoneOffset.UTC).toInstant();

        return tenantRepository.findDetailWithQuota(tenantId)
                .switchIfEmpty(Mono.error(BizException.notFound("TENANT_NOT_FOUND", "租户不存在")))
                .flatMap(detail -> Mono.zip(
                                callLogRepository.countByTenantIdAndTimeRange(tenantId, todayStart, tomorrowStart),
                                callLogRepository.countByTenantIdAndTimeRange(tenantId, monthStart, nextMonthStart),
                                appKeyRepository.countByTenantId(tenantId),
                                userRepository.findTenantAdminEmailsByTenantId(tenantId).collectList()
                        )
                        .map(tuple -> {
                            detail.setDailyUsage(tuple.getT1());
                            detail.setMonthlyUsage(tuple.getT2());
                            detail.setAppKeyCount(tuple.getT3());
                            detail.setTenantAdminEmails(tuple.getT4());
                            return detail;
                        }));
    }

    @Override
    public Mono<AdminTenantSummaryResponse> suspendTenant(Long tenantId) {
        return changeTenantStatus(tenantId, "SUSPENDED");
    }

    @Override
    public Mono<AdminTenantSummaryResponse> resumeTenant(Long tenantId) {
        return changeTenantStatus(tenantId, "ACTIVE");
    }

    @Override
    public Mono<AdminOverviewMetricsResponse> getOverview() {
        return tenantRepository.getOverviewMetrics();
    }

    private Mono<AdminTenantSummaryResponse> changeTenantStatus(Long tenantId, String targetStatus) {
        return tenantRepository.findById(tenantId)
                .switchIfEmpty(Mono.error(BizException.notFound("TENANT_NOT_FOUND", "租户不存在")))
                .flatMap(tenant -> {
                    if (targetStatus.equalsIgnoreCase(tenant.getStatus())) {
                        return tenantRepository.findSummaryById(tenantId);
                    }
                    return tenantRepository.update(tenantId, Update.update("status", targetStatus))
                            .then(tenantRepository.findSummaryById(tenantId))
                            .doOnSuccess(summary -> publishStatusChanged(tenant, targetStatus));
                });
    }

    private void publishStatusChanged(Tenant tenant, String targetStatus) {
        if ("SUSPENDED".equalsIgnoreCase(targetStatus)) {
            domainEventPublisher.publish(new TenantSuspendedEvent(tenant.getId(), Instant.now()));
        } else if ("ACTIVE".equalsIgnoreCase(targetStatus)) {
            domainEventPublisher.publish(new TenantResumedEvent(tenant.getId(), Instant.now()));
        }
    }
}
