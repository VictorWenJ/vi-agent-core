package com.vi.agent.core.model.prompt;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * prompt 渲染审计元数据。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PromptRenderMetadata {

    /** 当前模板 key。 */
    SystemPromptKey promptKey;

    /** prompt 用途。 */
    PromptPurpose purpose;

    /** 结构化输出契约 key。 */
    StructuredLlmOutputContractKey structuredOutputContractKey;

    /** 模板正文内容摘要。 */
    String templateContentHash;

    /** manifest 内容摘要。 */
    String manifestContentHash;

    /** contract 内容摘要。 */
    String contractContentHash;

    /** prompt catalog 修订标识。 */
    String catalogRevision;

    /** 本次参与渲染的变量名。 */
    @Singular("renderedVariableName")
    List<String> renderedVariableNames;
}
