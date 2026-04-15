package com.vi.agent.core.common.util;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Json 工具。
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        OBJECT_MAPPER.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private JsonUtils() {
    }

    public static String toJson(Object object) {
        if (Objects.isNull(object)) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            log.error("JsonUtils toJson error", e);
            throw new AgentRuntimeException(
                ErrorCode.RUNTIME_EXECUTION_FAILED,
                "JsonUtils toJson error, object: " + object,
                e
            );
        }
    }

    public static <T> T jsonToBean(String json, Type type) {
        if (json == null || json.isBlank() || Objects.isNull(type)) {
            return null;
        }
        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
            return OBJECT_MAPPER.readValue(json, javaType);
        } catch (Exception e) {
            log.error("JsonUtils jsonToBean error", e);
            throw new AgentRuntimeException(
                ErrorCode.RUNTIME_EXECUTION_FAILED,
                "JsonUtils jsonToBean error, json: " + json + " type: " + type.getTypeName(),
                e
            );
        }
    }
}
