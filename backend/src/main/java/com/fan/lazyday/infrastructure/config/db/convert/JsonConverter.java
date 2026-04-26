package com.fan.lazyday.infrastructure.config.db.convert;

import jakarta.annotation.Nonnull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import io.r2dbc.postgresql.codec.Json;

@WritingConverter
@ReadingConverter
public class JsonConverter implements Converter<Json, Json> {
    public Json convert(@Nonnull Json source) {
        return source;
    }
}
