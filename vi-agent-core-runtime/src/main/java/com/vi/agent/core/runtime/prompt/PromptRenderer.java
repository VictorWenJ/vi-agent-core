package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptMessageTemplate;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统 prompt 渲染器。
 */
public class PromptRenderer {

    /** prompt 占位符匹配表达式。 */
    private static final Pattern PLACEHOLDER_PATTERN =
        Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)}}");

    /** 系统 prompt 运行期只读注册表。 */
    private final SystemPromptRegistry systemPromptRegistry;

    /**
     * 构造系统 prompt 渲染器。
     */
    public PromptRenderer(SystemPromptRegistry systemPromptRegistry) {
        this.systemPromptRegistry = systemPromptRegistry;
    }

    /**
     * 渲染指定系统 prompt。
     */
    public PromptRenderResult render(PromptRenderRequest request) {
        if (request == null || request.getPromptKey() == null) {
            throw new PromptRenderException("prompt 渲染请求和 promptKey 不能为空");
        }
        AbstractPromptTemplate template = systemPromptRegistry.get(request.getPromptKey());
        Map<String, PromptInputVariable> variableDeclarations = toVariableMap(template.getInputVariables());
        Map<String, String> variables = request.getVariables() == null ? Map.of() : request.getVariables();
        validateRequestVariables(variables, variableDeclarations);
        validateRequiredVariables(variableDeclarations, variables);
        validateUntrustedVariables(variableDeclarations);

        if (template.getRenderOutputType() == PromptRenderOutputType.TEXT) {
            RenderedContent renderedContent = renderContent(template.getTextTemplate(), variableDeclarations, variables);
            return new TextPromptRenderResult(
                template.getPromptKey(),
                template.getPurpose(),
                metadata(template, renderedContent.renderedVariableNames()),
                renderedContent.content()
            );
        }
        return renderChatMessages(template, variableDeclarations, variables);
    }

    private PromptRenderResult renderChatMessages(
        AbstractPromptTemplate template,
        Map<String, PromptInputVariable> variableDeclarations,
        Map<String, String> variables
    ) {
        List<PromptRenderedMessage> messages = new ArrayList<>();
        Set<String> renderedVariableNames = new LinkedHashSet<>();
        for (PromptMessageTemplate messageTemplate : template.getMessageTemplates().stream()
            .sorted(Comparator.comparing(PromptMessageTemplate::getOrder))
            .toList()) {
            RenderedContent renderedContent = renderContent(
                messageTemplate.getContentTemplate(),
                variableDeclarations,
                variables
            );
            renderedVariableNames.addAll(renderedContent.renderedVariableNames());
            messages.add(PromptRenderedMessage.builder()
                .order(messageTemplate.getOrder())
                .role(messageTemplate.getRole())
                .renderedContent(renderedContent.content())
                .build());
        }
        StructuredLlmOutputContractKey contractKey = template.getStructuredOutputContractKey();
        return new ChatMessagesPromptRenderResult(
            template.getPromptKey(),
            template.getPurpose(),
            metadata(template, new ArrayList<>(renderedVariableNames)),
            messages,
            contractKey
        );
    }

    private RenderedContent renderContent(
        String templateContent,
        Map<String, PromptInputVariable> variableDeclarations,
        Map<String, String> variables
    ) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateContent == null ? "" : templateContent);
        StringBuffer buffer = new StringBuffer();
        List<String> renderedVariableNames = new ArrayList<>();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            PromptInputVariable declaration = variableDeclarations.get(variableName);
            if (declaration == null) {
                throw new PromptRenderException("模板存在未声明占位符: " + variableName);
            }
            String renderedValue = resolveVariableValue(declaration, variables);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(renderedValue));
            if (!renderedVariableNames.contains(variableName)) {
                renderedVariableNames.add(variableName);
            }
        }
        matcher.appendTail(buffer);
        return new RenderedContent(buffer.toString(), renderedVariableNames);
    }

    private Map<String, PromptInputVariable> toVariableMap(List<PromptInputVariable> inputVariables) {
        Map<String, PromptInputVariable> variableDeclarations = new LinkedHashMap<>();
        for (PromptInputVariable variable : inputVariables) {
            variableDeclarations.put(variable.getVariableName(), variable);
        }
        return variableDeclarations;
    }

    private void validateRequestVariables(
        Map<String, String> variables,
        Map<String, PromptInputVariable> variableDeclarations
    ) {
        for (String variableName : variables.keySet()) {
            if (!variableDeclarations.containsKey(variableName)) {
                throw new PromptRenderException("请求传入未声明变量: " + variableName);
            }
        }
    }

    private void validateRequiredVariables(
        Map<String, PromptInputVariable> variableDeclarations,
        Map<String, String> variables
    ) {
        for (PromptInputVariable variable : variableDeclarations.values()) {
            if (Boolean.TRUE.equals(variable.getRequired())
                && !variables.containsKey(variable.getVariableName())
                && variable.getDefaultValue() == null) {
                throw new PromptRenderException("必填变量缺失: " + variable.getVariableName());
            }
        }
    }

    private void validateUntrustedVariables(Map<String, PromptInputVariable> variableDeclarations) {
        for (PromptInputVariable variable : variableDeclarations.values()) {
            if (variable.getTrustLevel() != PromptInputTrustLevel.UNTRUSTED_DATA) {
                continue;
            }
            if (variable.getPlacement() == PromptInputPlacement.INSTRUCTION_BLOCK) {
                throw new PromptRenderException("UNTRUSTED_DATA 变量不得进入 instruction_block: " + variable.getVariableName());
            }
            if (variable.getMaxChars() == null) {
                throw new PromptRenderException("UNTRUSTED_DATA 变量缺少 maxChars: " + variable.getVariableName());
            }
            if (variable.getTruncateMarker() == null) {
                throw new PromptRenderException("UNTRUSTED_DATA 变量缺少 truncateMarker: " + variable.getVariableName());
            }
        }
    }

    private String resolveVariableValue(
        PromptInputVariable declaration,
        Map<String, String> variables
    ) {
        String value = variables.containsKey(declaration.getVariableName())
            ? variables.get(declaration.getVariableName())
            : declaration.getDefaultValue();
        if (value == null) {
            value = "";
        }
        if (declaration.getTrustLevel() == PromptInputTrustLevel.UNTRUSTED_DATA
            && declaration.getMaxChars() != null
            && value.length() > declaration.getMaxChars()) {
            return value.substring(0, declaration.getMaxChars()) + declaration.getTruncateMarker();
        }
        return value;
    }

    private PromptRenderMetadata metadata(
        AbstractPromptTemplate template,
        List<String> renderedVariableNames
    ) {
        return PromptRenderMetadata.builder()
            .promptKey(template.getPromptKey())
            .purpose(template.getPurpose())
            .structuredOutputContractKey(template.getStructuredOutputContractKey())
            .templateContentHash(systemPromptRegistry.templateContentHash(template.getPromptKey()))
            .manifestContentHash(systemPromptRegistry.manifestContentHash(template.getPromptKey()))
            .contractContentHash(systemPromptRegistry.contractContentHash(template.getStructuredOutputContractKey()))
            .catalogRevision(systemPromptRegistry.catalogRevision())
            .renderedVariableNames(renderedVariableNames)
            .build();
    }

    /**
     * 单段模板渲染结果。
     */
    private record RenderedContent(
        /** 渲染后内容。 */
        String content,

        /** 已渲染变量名。 */
        List<String> renderedVariableNames
    ) {
    }
}
