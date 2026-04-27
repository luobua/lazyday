package com.fan.lazyday.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantContext 单元测试
 * 验证 Reactor Context 的写入和读取行为（修复 ClassCastException 后的回归测试）
 */
class TenantContextTest {

    private static final Long USER_ID = 3333L;
    private static final Long TENANT_ID = 100L;
    private static final String ROLE = "TENANT_ADMIN";

    @Test
    @DisplayName("write + current: 写入 Context 后能正确读回")
    void writeAndCurrent_shouldRoundtrip() {
        reactor.util.context.Context ctx = TenantContext.write(USER_ID, TENANT_ID, ROLE);

        TenantContext result = Mono.deferContextual(c ->
                        c.getOrEmpty(TenantContext.CONTEXT_KEY)
                                .map(o -> (TenantContext) o)
                                .map(Mono::just)
                                .orElse(Mono.empty())
                )
                .contextWrite(ctx)
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getRole()).isEqualTo(ROLE);
    }

    @Test
    @DisplayName("current: Context 中无 key 时返回 Mono.empty()")
    void current_withoutContext_shouldReturnEmpty() {
        TenantContext result = TenantContext.current().block();

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("current: 使用 StepVerifier 验证空 Context 返回空")
    void current_withoutContext_stepVerifier() {
        StepVerifier.create(TenantContext.current())
                .verifyComplete();
    }

    @Test
    @DisplayName("current: 使用 flatMap + contextWrite 验证有 Context 时正确返回")
    void current_withContext_flatMap() {
        reactor.util.context.Context ctx = TenantContext.write(USER_ID, TENANT_ID, ROLE);

        TenantContext result = Mono.just("trigger")
                .flatMap(s -> TenantContext.current())
                .contextWrite(ctx)
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getRole()).isEqualTo(ROLE);
    }

    @Test
    @DisplayName("CONTEXT_KEY 应等于 TenantContext 全限定类名")
    void contextKey_shouldBeFullyQualifiedName() {
        assertThat(TenantContext.CONTEXT_KEY)
                .isEqualTo("com.fan.lazyday.infrastructure.security.TenantContext");
    }

    @Test
    @DisplayName("write: 不同参数产生不同实例")
    void write_differentParams_shouldCreateDifferentInstances() {
        Long anotherUserId = 4444L;
        reactor.util.context.Context ctx1 = TenantContext.write(USER_ID, TENANT_ID, ROLE);
        reactor.util.context.Context ctx2 = TenantContext.write(anotherUserId, 200L, "PLATFORM_ADMIN");

        TenantContext result1 = TenantContext.current().contextWrite(ctx1).block();
        TenantContext result2 = TenantContext.current().contextWrite(ctx2).block();

        assertThat(result1.getUserId()).isNotEqualTo(result2.getUserId());
        assertThat(result1.getTenantId()).isNotEqualTo(result2.getTenantId());
        assertThat(result1.getRole()).isNotEqualTo(result2.getRole());
    }
}
