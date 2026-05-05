package com.fan.lazyday.infrastructure.config.db.convert;

import io.r2dbc.postgresql.codec.Json;
import jakarta.annotation.Nonnull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.nio.charset.StandardCharsets;

/**
 * R2DBC ReadingConverter: Json (PostgreSQL jsonb) → String
 * <p>
 * R2DBC-PostgreSQL 驱动将 jsonb 列读取为 {@link Json} 类型，直接转 String 时
 * 默认走 Jackson 序列化 JsonNode 内部属性（bug）。
 * 此 Converter 通过 {@link Json#asArray()} 取原始字节，正确还原 JSON 字符串。
 * </p>
 */
@ReadingConverter
public class JsonToStringConverter implements Converter<Json, String> {

    @Override
    public String convert(@Nonnull Json source) {
        return new String(source.asArray(), StandardCharsets.UTF_8);
    }
}
