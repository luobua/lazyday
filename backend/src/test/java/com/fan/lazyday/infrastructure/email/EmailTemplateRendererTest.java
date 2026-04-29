package com.fan.lazyday.infrastructure.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    @Test
    @DisplayName("render: 渲染注册验证模板，包含品牌、邮箱、验证链接和过期时间")
    void renderRegistrationVerify_shouldIncludeExpectedContent() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(templateEngine());

        String html = renderer.render("registration-verify", Map.of(
                "userEmail", "new@example.com",
                "verifyUrl", "https://portal.lazyday.dev/verify?token=abc",
                "expiresInHours", 24
        ));

        assertThat(html).contains("Lazyday");
        assertThat(html).contains("new@example.com");
        assertThat(html).contains("https://portal.lazyday.dev/verify?token=abc");
        assertThat(html).contains("24");
    }

    @Test
    @DisplayName("render: 渲染配额耗尽和 webhook 永久失败模板")
    void renderNotificationTemplates_shouldIncludeDomainFields() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(templateEngine());

        String quota = renderer.render("quota-exceeded", Map.of(
                "tenantName", "Acme",
                "period", "day",
                "limit", 1000,
                "usage", 1001,
                "portalUrl", "https://portal.lazyday.dev/quota"
        ));
        String webhook = renderer.render("webhook-permanent-failed", Map.of(
                "tenantName", "Acme",
                "webhookName", "prod-hook",
                "webhookUrl", "https://example.com/webhook",
                "eventType", "appkey.disabled",
                "eventId", 101,
                "lastHttpStatus", 500,
                "lastError", "server error",
                "webhookConfigPortalUrl", "https://portal.lazyday.dev/webhooks?id=11"
        ));

        assertThat(quota).contains("Acme", "1000", "1001", "https://portal.lazyday.dev/quota");
        assertThat(webhook).contains("prod-hook", "appkey.disabled", "101", "server error");
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
