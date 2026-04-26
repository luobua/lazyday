package com.fan.lazyday.infrastructure.config.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;

public class InstantJacksonSerializer extends JsonSerializer<Instant> {

    private final DateTimeFormatter formatter;

    public InstantJacksonSerializer(JacksonProperties properties) {
        String dateFormat=properties.getDateFormat();
        TimeZone timeZone=properties.getTimeZone();
        formatter= DateTimeFormatter.ofPattern(Optional.ofNullable(dateFormat).orElse("yyyy-MM-dd HH:mm:ss"))
                .withZone(Optional.ofNullable(timeZone).orElse(TimeZone.getTimeZone(ZoneId.systemDefault())).toZoneId());
    }

    public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if(Objects.nonNull(instant)){
            jsonGenerator.writeString(formatter.format(instant));
        }
    }
}
