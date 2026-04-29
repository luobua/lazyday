package com.fan.lazyday.application.service.impl;

import com.fan.lazyday.application.service.EmailService;
import com.fan.lazyday.infrastructure.email.EmailTemplateRenderer;
import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final ServiceProperties serviceProperties;
    private final MailProperties mailProperties;

    @Override
    public Mono<Void> send(List<String> toAddresses, String subject, String templateName, Map<String, Object> model) {
        if (isTransportUnconfigured()) {
            log.warn("email transport unconfigured, emails will be discarded: template={}, recipients={}",
                    templateName, toAddresses == null ? 0 : toAddresses.size());
            return Mono.empty();
        }
        if (toAddresses == null || toAddresses.isEmpty()) {
            log.warn("email skipped because recipient list is empty: template={}", templateName);
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> doSend(toAddresses, subject, templateName, model))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void doSend(List<String> toAddresses, String subject, String templateName, Map<String, Object> model) {
        try {
            String html = templateRenderer.render(templateName, model == null ? Map.of() : model);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(serviceProperties.getEmail().getFrom());
            helper.setTo(toAddresses.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send email template " + templateName, ex);
        }
    }

    private boolean isTransportUnconfigured() {
        return isBlank(mailProperties.getHost())
                || isBlank(mailProperties.getUsername())
                || isBlank(mailProperties.getPassword());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
