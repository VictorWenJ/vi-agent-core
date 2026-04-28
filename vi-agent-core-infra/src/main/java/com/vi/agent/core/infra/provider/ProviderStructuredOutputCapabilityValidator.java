package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 请求前选择 provider structured output mode。
 */
public class ProviderStructuredOutputCapabilityValidator {

    private static final List<StructuredLlmOutputMode> DEFAULT_MODE_ORDER = List.of(
        StructuredLlmOutputMode.STRICT_TOOL_CALL,
        StructuredLlmOutputMode.JSON_SCHEMA_RESPONSE_FORMAT,
        StructuredLlmOutputMode.JSON_OBJECT
    );

    /** provider schema view 编译器。 */
    private final ProviderStructuredSchemaCompiler schemaCompiler;

    public ProviderStructuredOutputCapabilityValidator(ProviderStructuredSchemaCompiler schemaCompiler) {
        this.schemaCompiler = Objects.requireNonNull(schemaCompiler, "schemaCompiler must not be null");
    }

    /**
     * 根据 request 与 provider capability 选择本次请求使用的结构化输出模式。
     */
    public ProviderStructuredOutputSelection select(
        ModelRequest request,
        ProviderStructuredOutputCapability capability
    ) {
        if (request == null || request.getStructuredOutputContract() == null) {
            return ProviderStructuredOutputSelection.disabled();
        }
        ProviderStructuredOutputCapability actualCapability = capability == null
            ? ProviderStructuredOutputCapability.jsonObjectOnly("unknown", "unknown")
            : capability;
        StructuredLlmOutputContract contract = request.getStructuredOutputContract();
        String functionName = resolveFunctionName(request, contract);
        List<StructuredLlmOutputMode> orderedModes = orderedModes(request.getPreferredStructuredOutputMode());
        String lastFailureReason = null;
        for (StructuredLlmOutputMode mode : orderedModes) {
            if (!isSupported(mode, actualCapability)) {
                lastFailureReason = mode.getValue() + " is not supported";
                continue;
            }
            if (mode == StructuredLlmOutputMode.JSON_OBJECT) {
                return buildSelection(request, actualCapability, mode, functionName, null, null);
            }
            ProviderStructuredSchemaCompileResult compileResult = schemaCompiler.compile(
                contract,
                mode,
                actualCapability.getProviderName(),
                actualCapability.getModelName()
            );
            if (Boolean.TRUE.equals(compileResult.getAvailable())) {
                return buildSelection(request, actualCapability, mode, functionName, compileResult, null);
            }
            lastFailureReason = compileResult.getFailureReason();
        }
        return ProviderStructuredOutputSelection.builder()
            .enabled(false)
            .structuredOutputContractKey(contract.getStructuredOutputContractKey())
            .functionName(functionName)
            .providerName(actualCapability.getProviderName())
            .modelName(actualCapability.getModelName())
            .failureReason(lastFailureReason)
            .retryCount(0)
            .build();
    }

    private ProviderStructuredOutputSelection buildSelection(
        ModelRequest request,
        ProviderStructuredOutputCapability capability,
        StructuredLlmOutputMode mode,
        String functionName,
        ProviderStructuredSchemaCompileResult compileResult,
        String failureReason
    ) {
        StructuredLlmOutputContract contract = request.getStructuredOutputContract();
        return ProviderStructuredOutputSelection.builder()
            .enabled(true)
            .structuredOutputContractKey(contract.getStructuredOutputContractKey())
            .selectedStructuredOutputMode(mode)
            .providerSchemaView(compileResult == null ? null : compileResult.getProviderSchemaView())
            .providerSchemaViewJson(compileResult == null ? null : compileResult.getProviderSchemaViewJson())
            .functionName(functionName)
            .functionDescription(StringUtils.defaultIfBlank(contract.getDescription(), "Structured output function."))
            .providerName(capability.getProviderName())
            .modelName(capability.getModelName())
            .failureReason(failureReason)
            .retryCount(0)
            .build();
    }

    private List<StructuredLlmOutputMode> orderedModes(StructuredLlmOutputMode preferredMode) {
        if (preferredMode == null) {
            return DEFAULT_MODE_ORDER;
        }
        List<StructuredLlmOutputMode> modes = new ArrayList<>();
        modes.add(preferredMode);
        for (StructuredLlmOutputMode mode : DEFAULT_MODE_ORDER) {
            if (mode != preferredMode) {
                modes.add(mode);
            }
        }
        return modes;
    }

    private boolean isSupported(StructuredLlmOutputMode mode, ProviderStructuredOutputCapability capability) {
        return switch (mode) {
            case STRICT_TOOL_CALL -> Boolean.TRUE.equals(capability.getSupportsStrictToolCall());
            case JSON_SCHEMA_RESPONSE_FORMAT -> Boolean.TRUE.equals(capability.getSupportsJsonSchemaResponseFormat());
            case JSON_OBJECT -> Boolean.TRUE.equals(capability.getSupportsJsonObject());
        };
    }

    private String resolveFunctionName(ModelRequest request, StructuredLlmOutputContract contract) {
        if (StringUtils.isNotBlank(request.getStructuredOutputFunctionName())) {
            return request.getStructuredOutputFunctionName();
        }
        StructuredLlmOutputContractKey contractKey = contract.getStructuredOutputContractKey();
        if (contractKey == StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT) {
            return "emit_state_delta";
        }
        if (contractKey == StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT) {
            return "emit_conversation_summary";
        }
        return "emit_structured_output";
    }
}
