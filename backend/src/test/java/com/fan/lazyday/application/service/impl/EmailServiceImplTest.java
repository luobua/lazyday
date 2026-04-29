package com.fan.lazyday.application.service.impl;

import com.fan.lazyday.infrastructure.email.EmailTemplateRenderer;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private EmailTemplateRenderer renderer;

    private ServiceProperties serviceProperties;
    private MailProperties mailProperties;

    @BeforeEach
    void setUp() {
        serviceProperties = new ServiceProperties();
        serviceProperties.getEmail().setFrom("noreply@lazyday.dev");
        mailProperties = new MailProperties();
    }

    @Test
    @DisplayName("send: SMTP host 未配置时跳过并返回成功")
    void send_unconfiguredSmtp_shouldSkip() {
        EmailServiceImpl emailService = new EmailServiceImpl(mailSender, renderer, serviceProperties, mailProperties);

        StepVerifier.create(emailService.send(List.of("admin@example.com"), "subject", "quota-exceeded", Map.of()))
                .verifyComplete();

        verify(renderer, never()).render(any(), any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("send: SMTP 已配置时渲染 HTML 并发送 MimeMessage")
    void send_configuredSmtp_shouldSendHtmlMessage() {
        mailProperties.setHost("smtp.example.com");
        mailProperties.setUsername("user");
        mailProperties.setPassword("secret");
        when(renderer.render(eq("quota-exceeded"), any())).thenReturn("<html>quota</html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        EmailServiceImpl emailService = new EmailServiceImpl(mailSender, renderer, serviceProperties, mailProperties);

        StepVerifier.create(emailService.send(List.of("admin@example.com"), "subject", "quota-exceeded", Map.of("tenantName", "Acme")))
                .verifyComplete();

        verify(renderer).render(eq("quota-exceeded"), any());
        verify(mailSender).send(any(MimeMessage.class));
    }
}
