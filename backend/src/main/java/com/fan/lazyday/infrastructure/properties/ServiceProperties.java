package com.fan.lazyday.infrastructure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * <p>描述: [配置文件] </p>
 * <p>创建时间: 2024/10/21 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/10/21 13:41 fan 创建
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fan.service")
public class ServiceProperties implements InitializingBean {
    private String domainHost;
    private String accessKey;
    private String secretKey;
    private String encryptionKey;
    private String contextPathV1;
    private String contextPathV2;
    private String openContextPathV1;
    private String visitorContextPathV1;
    private String portalContextPathV1;
    private String adminContextPathV1;
    private String internalContextPathV1;
    private String internalApiKey;
    private int callLogBufferSize = 10_000;
    private Snowflake snowflake = new Snowflake();
    private Webhook webhook = new Webhook();
    private Email email = new Email();

    @Override
    public void afterPropertiesSet() throws Exception {
        Objects.requireNonNull(domainHost, "domainHost is required");
        Objects.requireNonNull(accessKey, "accessKey is required");
        Objects.requireNonNull(secretKey, "secretKey is required");
        Objects.requireNonNull(contextPathV1, "contextPathV1 is required");
        Objects.requireNonNull(openContextPathV1, "openContextPathV1 is required");
        Objects.requireNonNull(internalContextPathV1, "internalContextPathV1 is required");
        Objects.requireNonNull(internalApiKey, "internalApiKey is required");
        if (internalApiKey.length() < 32) {
            throw new BeanInitializationException("internalApiKey must be at least 32 characters");
        }
        if (snowflake.workerId == null || snowflake.dataCenterId == null) {
            throw new IllegalStateException("fan.service.snowflake.worker-id and data-center-id are required");
        }
    }

    @Getter
    @Setter
    public static class Snowflake {
        private Long workerId;
        private Long dataCenterId;
    }

    @Getter
    @Setter
    public static class Webhook {
        private boolean dispatchEnabled = true;
        private int dispatchIntervalSeconds = 5;
        private int httpTimeoutMs = 10_000;
        private int maxRetries = 5;
        private String backoffSequence = "60,300,1800,7200,21600";
    }

    @Getter
    @Setter
    public static class Email {
        private String from = "noreply@lazyday.dev";
    }
}
