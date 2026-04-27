package com.fan.lazyday.infrastructure.properties;

import lombok.Getter;
import lombok.Setter;
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

    @Override
    public void afterPropertiesSet() throws Exception {
        Objects.requireNonNull(domainHost, "domainHost is required");
        Objects.requireNonNull(accessKey, "accessKey is required");
        Objects.requireNonNull(secretKey, "secretKey is required");
        Objects.requireNonNull(contextPathV1, "contextPathV1 is required");
        Objects.requireNonNull(openContextPathV1, "openContextPathV1 is required");
    }
}
