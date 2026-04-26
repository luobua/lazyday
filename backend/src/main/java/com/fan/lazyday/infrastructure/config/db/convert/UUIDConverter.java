package com.fan.lazyday.infrastructure.config.db.convert;

import java.util.UUID;

import jakarta.annotation.Nonnull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
@ReadingConverter
public class UUIDConverter implements Converter<UUID, UUID> {
    public UUID convert(@Nonnull UUID source) {
        return source;
    }
}
