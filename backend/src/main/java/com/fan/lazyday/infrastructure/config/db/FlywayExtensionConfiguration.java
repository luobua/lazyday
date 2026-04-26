package com.fan.lazyday.infrastructure.config.db;


import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.context.event.EventListener;

import java.util.Optional;

/**
 * <p>描述: [Fly扩展配置] </p>
 * <p>创建时间: 2024/8/23 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/08/23 13:31 fan 创建
 */
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({FlywayProperties.class})
public class FlywayExtensionConfiguration{
    private final Flyway flyway;
    private final FlywayProperties flywayProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        if (flyway.info().all().length > 0) {
            String dbType = flywayProperties.getUrl().split(":")[1].toLowerCase();
            Optional<String> optional = flywayProperties
                    .getLocations()
                    .stream()
                    .findFirst()
                    .map(location -> location.replaceAll("/[^/]+$", "/" + dbType));
            Flyway.configure()
                    .dataSource(
                            flywayProperties.getUrl(),
                            flywayProperties.getUser(),
                            flywayProperties.getPassword()
                    )
                    .locations(optional.orElse("classpath:db/migration/" + dbType))
                    .table(flywayProperties.getTable() + "_" + dbType)
                    .schemas(flywayProperties.getSchemas().toArray(new String[]{}))
                    .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                    .load()
                    .migrate();
        }
    }
}
