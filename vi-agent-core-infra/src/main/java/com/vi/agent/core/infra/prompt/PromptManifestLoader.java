package com.vi.agent.core.infra.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptInputVariableType;
import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * prompt manifest 与契约资源加载器。
 */
public final class PromptManifestLoader {

    /** prompt 占位符匹配表达式。 */
    private static final Pattern PLACEHOLDER_PATTERN =
        Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}");

    /** JSON 解析器，仅用于 E1 轻量结构校验。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PromptManifestLoader() {
    }

    /**
     * 解析 manifest.yml 内容。
     */
    public static PromptManifest loadManifest(String manifestContent) {
        String normalizedContent = normalizeContent(manifestContent);
        Object document = new Yaml().load(normalizedContent);
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("prompt manifest 顶层必须是 object");
        }
        Map<String, Object> values = toStringKeyMap(root);

        String promptKey = required(values, "promptKey");
        String purpose = required(values, "purpose");
        String renderOutputType = required(values, "renderOutputType");
        String structuredOutputContractKey = optional(values, "structuredOutputContractKey");

        return new PromptManifest(
            parseSystemPromptKey(promptKey),
            parsePromptPurpose(purpose),
            parsePromptRenderOutputType(renderOutputType),
            parseStructuredLlmOutputContractKey(structuredOutputContractKey, true),
            parseInputVariables(values),
            optional(values, "description")
        );
    }

    /**
     * 解析 contract.json 内容并保留完整 schema JSON。
     */
    public static StructuredLlmOutputContract loadContract(String contractContent) {
        String normalizedContent = normalizeContent(contractContent);
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(normalizedContent);
            if (rootNode == null || !rootNode.isObject()) {
                throw new IllegalStateException("contract.json 顶层必须是 JSON Schema object");
            }
            if (!rootNode.has("type") || !"object".equals(rootNode.path("type").asText())) {
                throw new IllegalStateException("contract.json 顶层必须直接声明 type: object");
            }
            String contractKeyValue = requiredText(rootNode, "x-structuredOutputContractKey");
            String outputTargetValue = requiredText(rootNode, "x-outputTarget");
            String description = requiredText(rootNode, "x-description");

            return StructuredLlmOutputContract.builder()
                .structuredOutputContractKey(parseStructuredLlmOutputContractKey(contractKeyValue, false))
                .outputTarget(parseStructuredLlmOutputTarget(outputTargetValue))
                .schemaJson(normalizedContent)
                .description(description)
                .build();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("contract.json 必须是可解析的 JSON object", ex);
        }
    }

    /**
     * 提取模板中的占位符变量名。
     */
    public static List<String> findPlaceholders(String templateContent) {
        List<String> variableNames = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateContent == null ? "" : templateContent);
        while (matcher.find()) {
            variableNames.add(matcher.group(1));
        }
        return variableNames;
    }

    /**
     * 按 UTF-8、LF、去 BOM、SHA-256 的固定口径计算内容摘要。
     */
    public static String sha256Hex(String content) {
        try {
            byte[] bytes = normalizeContent(content).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    /**
     * 规范化资源内容，避免本机换行和 BOM 影响 hash。
     */
    public static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content;
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<PromptInputVariable> parseInputVariables(Map<String, Object> values) {
        Object rawInputVariables = values.get("inputVariables");
        if (rawInputVariables == null) {
            return List.of();
        }
        if (!(rawInputVariables instanceof List<?> rawList)) {
            throw new IllegalStateException("prompt manifest inputVariables 必须是 list");
        }
        List<PromptInputVariable> inputVariables = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawFields)) {
                throw new IllegalStateException("prompt manifest inputVariables 元素必须是 object");
            }
            inputVariables.add(toInputVariable(toStringKeyMap(rawFields)));
        }
        return inputVariables;
    }

    private static PromptInputVariable toInputVariable(Map<String, Object> fields) {
        String variableName = required(fields, "variableName");
        return PromptInputVariable.builder()
            .variableName(variableName)
            .variableType(parsePromptInputVariableType(required(fields, "variableType")))
            .trustLevel(parsePromptInputTrustLevel(required(fields, "trustLevel")))
            .placement(parsePromptInputPlacement(required(fields, "placement")))
            .required(parseBoolean(optional(fields, "required")))
            .maxChars(parseInteger(optional(fields, "maxChars")))
            .truncateMarker(optional(fields, "truncateMarker"))
            .description(optional(fields, "description"))
            .defaultValue(optional(fields, "defaultValue"))
            .build();
    }

    private static String required(Map<String, Object> values, String key) {
        String value = optional(values, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("prompt manifest 缺少必要字段: " + key);
        }
        return value;
    }

    private static String optional(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return values;
    }

    private static String requiredText(JsonNode rootNode, String fieldName) {
        JsonNode valueNode = rootNode.get(fieldName);
        if (valueNode == null || !valueNode.isTextual() || valueNode.asText().isBlank()) {
            throw new IllegalStateException("contract.json 缺少必要扩展字段: " + fieldName);
        }
        return valueNode.asText();
    }

    private static Boolean parseBoolean(String value) {
        return value == null || value.isBlank() ? null : Boolean.parseBoolean(value);
    }

    private static Integer parseInteger(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static SystemPromptKey parseSystemPromptKey(String value) {
        for (SystemPromptKey item : SystemPromptKey.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 SystemPromptKey: " + value);
    }

    private static PromptPurpose parsePromptPurpose(String value) {
        for (PromptPurpose item : PromptPurpose.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 PromptPurpose: " + value);
    }

    private static PromptRenderOutputType parsePromptRenderOutputType(String value) {
        for (PromptRenderOutputType item : PromptRenderOutputType.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 PromptRenderOutputType: " + value);
    }

    private static PromptInputVariableType parsePromptInputVariableType(String value) {
        for (PromptInputVariableType item : PromptInputVariableType.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 PromptInputVariableType: " + value);
    }

    private static PromptInputTrustLevel parsePromptInputTrustLevel(String value) {
        for (PromptInputTrustLevel item : PromptInputTrustLevel.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 PromptInputTrustLevel: " + value);
    }

    private static PromptInputPlacement parsePromptInputPlacement(String value) {
        for (PromptInputPlacement item : PromptInputPlacement.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 PromptInputPlacement: " + value);
    }

    private static StructuredLlmOutputContractKey parseStructuredLlmOutputContractKey(
        String value,
        boolean nullable
    ) {
        if (value == null || value.isBlank()) {
            if (nullable) {
                return null;
            }
            throw new IllegalStateException("StructuredLlmOutputContractKey 不能为空");
        }
        for (StructuredLlmOutputContractKey item : StructuredLlmOutputContractKey.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 StructuredLlmOutputContractKey: " + value);
    }

    private static StructuredLlmOutputTarget parseStructuredLlmOutputTarget(String value) {
        for (StructuredLlmOutputTarget item : StructuredLlmOutputTarget.values()) {
            if (item.getValue().equals(value)) {
                return item;
            }
        }
        throw new IllegalStateException("未知 StructuredLlmOutputTarget: " + value);
    }

    /**
     * manifest.yml 中的系统 prompt 声明。
     */
    public static final class PromptManifest {

        /** 系统级 prompt key。 */
        private final SystemPromptKey promptKey;

        /** prompt 用途。 */
        private final PromptPurpose purpose;

        /** 渲染输出形态。 */
        private final PromptRenderOutputType renderOutputType;

        /** 结构化输出契约 key。 */
        private final StructuredLlmOutputContractKey structuredOutputContractKey;

        /** 输入变量声明。 */
        private final List<PromptInputVariable> inputVariables;

        /** 模板说明。 */
        private final String description;

        private PromptManifest(
            SystemPromptKey promptKey,
            PromptPurpose purpose,
            PromptRenderOutputType renderOutputType,
            StructuredLlmOutputContractKey structuredOutputContractKey,
            List<PromptInputVariable> inputVariables,
            String description
        ) {
            this.promptKey = promptKey;
            this.purpose = purpose;
            this.renderOutputType = renderOutputType;
            this.structuredOutputContractKey = structuredOutputContractKey;
            this.inputVariables = inputVariables == null ? List.of() : List.copyOf(inputVariables);
            this.description = description == null ? "" : description;
        }

        /**
         * 返回系统级 prompt key。
         */
        public SystemPromptKey promptKey() {
            return promptKey;
        }

        /**
         * 返回 prompt 用途。
         */
        public PromptPurpose purpose() {
            return purpose;
        }

        /**
         * 返回渲染输出形态。
         */
        public PromptRenderOutputType renderOutputType() {
            return renderOutputType;
        }

        /**
         * 返回结构化输出契约 key。
         */
        public StructuredLlmOutputContractKey structuredOutputContractKey() {
            return structuredOutputContractKey;
        }

        /**
         * 返回输入变量声明。
         */
        public List<PromptInputVariable> inputVariables() {
            return inputVariables;
        }

        /**
         * 返回模板说明。
         */
        public String description() {
            return description;
        }
    }
}
