package com.fan.lazyday.infrastructure.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author chenbin
 */
public class JsonUtils {
    private static final ThreadLocal<ObjectMapper> LOCAL_SERIALIZE_MAPPER = ThreadLocal.withInitial(() -> (new ObjectMapper()).findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS));
    private static final ThreadLocal<ObjectMapper> LOCAL_DESERIALIZE_MAPPER = ThreadLocal.withInitial(() -> (new ObjectMapper()).findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true));

    private JsonUtils() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    public static String toJSONString(Object object) {
        try {
            return LOCAL_SERIALIZE_MAPPER.get().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        }
    }

    public static byte[] toJSONBytes(Object object) {
        try {
            return LOCAL_SERIALIZE_MAPPER.get().writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        }
    }

    public static <T> T parse(String text, Class<T> valueType) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(text, valueType);
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        }
    }

    public static <T> T parse(byte[] bytes, Class<T> valueType) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(bytes, valueType);
        } catch (IOException e) {
            throw new JsonException(e);
        }
    }

    public static <T> T parse(String text, TypeReference<T> typeRef) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(text, typeRef);
        } catch (IOException e) {
            throw new JsonException(e);
        }
    }

    public static <T> T parse(byte[] bytes, TypeReference<T> typeRef) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(bytes, typeRef);
        } catch (IOException e) {
            throw new JsonException(e);
        }
    }

    public static Map<String, Object> parseAsMap(String text) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(text, mapType());
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        }
    }

    public static <T> Map<String, T> parseAsMap(String text, Class<T> valueType) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(text, mapType(valueType));
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        }
    }

    public static <T> List<T> parseAsList(String text, Class<T> valueType) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(text, listType(valueType));
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        }
    }

    public static <T> List<T> parseAsList(byte[] bytes, Class<T> valueType) {
        try {
            return LOCAL_DESERIALIZE_MAPPER.get().readValue(bytes, listType(valueType));
        } catch (IOException e) {
            throw new JsonException(e);
        }
    }

    public static <T> JavaType listType(Class<T> valueType) {
        return LOCAL_DESERIALIZE_MAPPER.get().getTypeFactory().constructCollectionType(List.class, valueType);
    }

    public static <T> JavaType listOfMapType(Class<T> valueType) {
        return LOCAL_DESERIALIZE_MAPPER.get().getTypeFactory().constructRawCollectionType(List.class);
    }

    public static JavaType mapType() {
        return LOCAL_DESERIALIZE_MAPPER.get().getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
    }

    public static <T> JavaType mapType(Class<T> valueType) {
        return LOCAL_DESERIALIZE_MAPPER.get().getTypeFactory().constructMapType(LinkedHashMap.class, String.class, valueType);
    }

    public static <T> T convertValue(Object obj, TypeReference<T> reparams) {
        return LOCAL_DESERIALIZE_MAPPER.get().convertValue(obj, reparams);
    }

    public static <T> T readValue(String obj, TypeReference<T> params) throws JsonProcessingException {
        return LOCAL_DESERIALIZE_MAPPER.get().readValue(obj, params);
    }

    public static <T> T clone(T obj, Class<T> clz) {
        return parse(toJSONBytes(obj), clz);
    }

    public static <T> T readerForUpdating(T t, String json) {
        return readerForUpdating(t, json, false);
    }

    public static <T> T readerForUpdating(T t, String json, boolean isExcludeEmpty) {
        if (isExcludeEmpty) {
            return readerForUpdatingExcludeEmpty(t, json);
        } else {
            try {
                return LOCAL_DESERIALIZE_MAPPER.get()
                        .readerForUpdating(t)
                        .readValue(json);
            } catch (JsonProcessingException e) {
                throw new JsonException(e);
            }
        }
    }

    public static <T> T readerForUpdatingExcludeEmpty(T t, String json) {
        try {
            JsonNode node = LOCAL_DESERIALIZE_MAPPER.get().readTree(json);
            if (node != null && node.isObject()) {
                ObjectNode objectNode = (ObjectNode) node;

                // 移除 null 字段
                objectNode.properties().removeIf(entry -> entry.getValue().isNull());

                return LOCAL_DESERIALIZE_MAPPER.get()
                        .readerForUpdating(t).readValue(objectNode);
            }
            return t;
        } catch (JsonProcessingException e) {
            throw new JsonException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class JsonException extends RuntimeException {
        public JsonException(Throwable cause) {
            super(cause);
        }
    }
}
