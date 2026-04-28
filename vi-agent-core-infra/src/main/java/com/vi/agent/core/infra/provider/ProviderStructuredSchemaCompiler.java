package com.vi.agent.core.infra.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 将业务结构化输出契约编译为 provider 请求使用的 schema view。
 */
public class ProviderStructuredSchemaCompiler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 编译 provider-specific schema view。
     *
     * @param contract 结构化输出契约
     * @param mode     目标结构化输出模式
     * @param provider provider 名称
     * @param model    模型名称
     * @return 编译结果
     */
    public ProviderStructuredSchemaCompileResult compile(
        StructuredLlmOutputContract contract,
        StructuredLlmOutputMode mode,
        String provider,
        String model
    ) {
        if (contract == null || StringUtils.isBlank(contract.getSchemaJson())) {
            return unavailable(mode, "structured output schema is blank");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contract.getSchemaJson());
            if (!root.isObject()) {
                return unavailable(mode, "structured output schema must be JSON object");
            }
            ObjectNode providerSchemaView = ((ObjectNode) root).deepCopy();
            stripExtensionMetadata(providerSchemaView);
            if (mode == StructuredLlmOutputMode.STRICT_TOOL_CALL) {
                String strictFailureReason = validateStrictCompatible(providerSchemaView);
                if (strictFailureReason != null) {
                    return unavailable(mode, strictFailureReason);
                }
            }
            String schemaViewJson = OBJECT_MAPPER.writeValueAsString(providerSchemaView);
            Object schemaViewObject = OBJECT_MAPPER.convertValue(providerSchemaView, Object.class);
            return ProviderStructuredSchemaCompileResult.builder()
                .available(true)
                .structuredOutputMode(mode)
                .providerSchemaView(schemaViewObject)
                .providerSchemaViewJson(schemaViewJson)
                .build();
        } catch (Exception ex) {
            return unavailable(mode, "structured output schema compile failed: " + ex.getMessage());
        }
    }

    private ProviderStructuredSchemaCompileResult unavailable(StructuredLlmOutputMode mode, String failureReason) {
        return ProviderStructuredSchemaCompileResult.builder()
            .available(false)
            .structuredOutputMode(mode)
            .failureReason(failureReason)
            .build();
    }

    /**
     * 剥离 provider 请求不应携带的 x-* 契约元数据。
     */
    private void stripExtensionMetadata(ObjectNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().startsWith("x-")) {
                fields.remove();
                continue;
            }
            JsonNode value = field.getValue();
            if (value.isObject()) {
                stripExtensionMetadata((ObjectNode) value);
            } else if (value.isArray()) {
                for (JsonNode item : value) {
                    if (item.isObject()) {
                        stripExtensionMetadata((ObjectNode) item);
                    }
                }
            }
        }
    }

    /**
     * 校验当前 schema view 是否可安全作为 strict tool call parameters。
     */
    private String validateStrictCompatible(JsonNode node) {
        if (!"object".equals(node.path("type").asText())) {
            return "strict tool call schema root must be object";
        }
        if (!node.has("additionalProperties") || node.path("additionalProperties").asBoolean(true)) {
            return "strict tool call object schema must declare additionalProperties:false";
        }
        return validateStrictNode(node);
    }

    private String validateStrictNode(JsonNode node) {
        if (node.isObject()) {
            if (node.has("oneOf")) {
                return "composition keyword unsupported by current compiler: oneOf";
            }
            if (node.has("anyOf")) {
                return "composition keyword unsupported by current compiler: anyOf";
            }
            if (node.has("allOf")) {
                return "composition keyword unsupported by current compiler: allOf";
            }
            if (node.path("type").isArray()) {
                return "type array unsupported in strict mode";
            }
            String unsupportedKeyword = findUnsupportedStrictKeyword(node);
            if (unsupportedKeyword != null) {
                return unsupportedKeyword + " unsupported in strict mode";
            }
            String type = node.path("type").asText();
            if ("object".equals(type)) {
                String objectFailureReason = validateStrictObject(node);
                if (objectFailureReason != null) {
                    return objectFailureReason;
                }
            }
            if ("string".equals(type)) {
                if (node.has("minLength")) {
                    return "string minLength unsupported in strict mode";
                }
                if (node.has("maxLength")) {
                    return "string maxLength unsupported in strict mode";
                }
            }
            if ("array".equals(type)) {
                if (node.has("minItems")) {
                    return "array minItems unsupported in strict mode";
                }
                if (node.has("maxItems")) {
                    return "array maxItems unsupported in strict mode";
                }
            }
            JsonNode propertiesNode = node.path("properties");
            if (propertiesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                while (fields.hasNext()) {
                    String failureReason = validateStrictNode(fields.next().getValue());
                    if (failureReason != null) {
                        return failureReason;
                    }
                }
            }
            JsonNode itemsNode = node.path("items");
            if (itemsNode.isObject()) {
                String failureReason = validateStrictNode(itemsNode);
                if (failureReason != null) {
                    return failureReason;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String failureReason = validateStrictNode(item);
                if (failureReason != null) {
                    return failureReason;
                }
            }
        }
        return null;
    }

    private String validateStrictObject(JsonNode node) {
        if (!node.has("additionalProperties") || node.path("additionalProperties").asBoolean(true)) {
            return "object schema must declare additionalProperties:false";
        }
        if (!node.has("properties") || !node.path("properties").isObject()) {
            return "object schema must declare properties";
        }
        Set<String> propertyNames = fieldNames(node.path("properties"));
        Set<String> requiredNames = requiredNames(node.path("required"));
        for (String propertyName : propertyNames) {
            if (!requiredNames.contains(propertyName)) {
                return "object required missing property: " + propertyName;
            }
        }
        for (String requiredName : requiredNames) {
            if (!propertyNames.contains(requiredName)) {
                return "object required unknown property: " + requiredName;
            }
        }
        return null;
    }

    private Set<String> fieldNames(JsonNode propertiesNode) {
        Set<String> names = new LinkedHashSet<>();
        Iterator<String> iterator = propertiesNode.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }

    private Set<String> requiredNames(JsonNode requiredNode) {
        Set<String> names = new LinkedHashSet<>();
        if (requiredNode == null || !requiredNode.isArray()) {
            return names;
        }
        for (JsonNode requiredItem : requiredNode) {
            names.add(requiredItem.asText());
        }
        return names;
    }

    private String findUnsupportedStrictKeyword(JsonNode node) {
        Set<String> supportedKeywords = Set.of(
            "$schema",
            "type",
            "additionalProperties",
            "properties",
            "required",
            "items",
            "enum",
            "const",
            "description"
        );
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!supportedKeywords.contains(fieldName)) {
                return fieldName;
            }
        }
        return null;
    }
}
