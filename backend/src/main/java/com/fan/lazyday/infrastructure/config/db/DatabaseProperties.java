package com.fan.lazyday.infrastructure.config.db;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>描述: [自定义配置属性] </p>
 * <p>创建时间: 2024/9/12 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/12 16:53 fan 创建
 */
@Getter
@Setter
@Component
@ConfigurationProperties("database")
@SuppressWarnings("all")
public class DatabaseProperties {

    private String type;
    private String host;
    private int port;
    private String dbname;
    private String username;
    private String password;
    private String currentSchema;
    private boolean uuidToString = false;
}
