package com.vi.agent.core.runtime.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 基于 JSON Schema 的结构化 LLM 输出契约守卫。
 */
@Component
public class StructuredLlmOutputContractGuard {

    /** JSON 解析器，ObjectMapper 线程安全可复用。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** JSON Schema validator 注册表。 */
    private static final SchemaRegistry SCHEMA_REGISTRY =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    /**
     * 校验归一化结构化输出是否符合指定 contract。
     */
    public StructuredLlmOutputContractValidationResult validate(
        StructuredLlmOutputContract contract,
        NormalizedStructuredLlmOutput output
    ) {
        StructuredLlmOutputContractKey contractKey = contract == null
            ? null
            : contract.getStructuredOutputContractKey();
        if (contract == null) {
            return StructuredLlmOutputContractValidationResult.failed(null, "structured output contract is null");
        }
        if (output == null) {
            return StructuredLlmOutputContractValidationResult.failed(contractKey, "structured output is null");
        }
        if (StringUtils.isBlank(output.getOutputJson())) {
            return StructuredLlmOutputContractValidationResult.failed(contractKey, "structured output JSON is blank");
        }
        if (output.getStructuredOutputContractKey() != contractKey) {
            return StructuredLlmOutputContractValidationResult.failed(
                contractKey,
                "structured output contract key mismatch, expected "
                    + valueOf(contractKey)
                    + " but actual "
                    + valueOf(output.getStructuredOutputContractKey())
            );
        }

        JsonNode outputNode = parseJson(output.getOutputJson());
        if (outputNode == null) {
            return StructuredLlmOutputContractValidationResult.failed(contractKey, "structured output JSON is invalid JSON");
        }
        if (!outputNode.isObject()) {
            return StructuredLlmOutputContractValidationResult.failed(contractKey, "structured output JSON must be JSON object");
        }

        JsonNode schemaNode = parseJson(contract.getSchemaJson());
        if (schemaNode == null || !schemaNode.isObject()) {
            return StructuredLlmOutputContractValidationResult.failed(contractKey, "structured output schemaJson is invalid JSON Schema object");
        }

        Schema schema = SCHEMA_REGISTRY.getSchema(validatorSchemaView(schemaNode));
        List<Error> errors = schema.validate(outputNode);
        if (errors.isEmpty()) {
            return StructuredLlmOutputContractValidationResult.passed(contractKey);
        }

        List<String> errorMessages = errors.stream()
            .map(Error::toString)
            .toList();
        return StructuredLlmOutputContractValidationResult.failed(
            contractKey,
            String.join("; ", errorMessages),
            errorMessages
        );
    }

    /**
     * 将字符串解析为 Jackson JSON 节点，解析失败返回 null。
     */
    private JsonNode parseJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 构建仅供 validator 使用的 schema 副本，原始 schemaJson 保持不变。
     */
    private JsonNode validatorSchemaView(JsonNode schemaNode) {
        JsonNode copy = schemaNode.deepCopy();
        removeExtensionKeywords(copy);
        return copy;
    }

    /**
     * 递归移除 schema 中的 x-* 扩展关键字，避免被当作模型输出字段。
     */
    private void removeExtensionKeywords(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> extensionFields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().startsWith("x-")) {
                    extensionFields.add(field.getKey());
                } else {
                    removeExtensionKeywords(field.getValue());
                }
            }
            objectNode.remove(extensionFields);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                removeExtensionKeywords(item);
            }
        }
    }

    /**
     * 输出契约 key 的稳定值。
     */
    private String valueOf(StructuredLlmOutputContractKey contractKey) {
        return contractKey == null ? "null" : contractKey.getValue();
    }
}
