package com.fan.lazyday.infrastructure.config.db;

import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author fanqibu
 */
public class R2dbcCustomConverterConfiguration {


    @Bean
    public R2dbcConfiguration.CustomR2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory, DatabaseProperties properties) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);

        Collection<?> converters = Arrays.asList(
                new StringToUUIDConverter()
        );

        List<Object> converterList = new ArrayList<>(converters);

        if (properties.isUuidToString()) {
            converterList.add(new UUIDToStringConverter());
        }


        return R2dbcConfiguration.CustomR2dbcCustomConversions
                .of(
                        dialect,
                        converterList
                );
    }

    @WritingConverter
    public static class UUIDToStringConverter implements Converter<UUID, String> {
        @Override
        public String convert(@Nonnull UUID source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToUUIDConverter implements Converter<String, UUID> {
        @SneakyThrows
        @Override
        public UUID convert(String source) {
            if (StringUtils.hasText(source)) {
                return UUID.fromString(source);
            }
            return null;
        }
    }
}
