package com.fan.lazyday.infrastructure.config;

import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import com.fan.lazyday.infrastructure.utils.id.SnowflakeIdWorker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(ServiceProperties serviceProperties) {
        ServiceProperties.Snowflake snowflake = serviceProperties.getSnowflake();
        if (snowflake.getWorkerId() == null || snowflake.getDataCenterId() == null) {
            throw new IllegalStateException("Snowflake workerId and dataCenterId are required");
        }
        return new SnowflakeIdWorker(snowflake.getWorkerId(), snowflake.getDataCenterId());
    }
}
