package com.vi.agent.core.infra.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.Map;

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
                return "strict tool call schema does not support oneOf";
            }
            if (node.has("anyOf")) {
                return "strict tool call schema does not support anyOf";
            }
            if (node.has("allOf")) {
                return "strict tool call schema does not support allOf";
            }
            if (node.path("type").isArray()) {
                return "strict tool call schema does not support type array";
            }
            if ("object".equals(node.path("type").asText())
                && (!node.has("additionalProperties") || node.path("additionalProperties").asBoolean(true))) {
                return "strict tool call object schema must declare additionalProperties:false";
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                String failureReason = validateStrictNode(fields.next().getValue());
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
}
