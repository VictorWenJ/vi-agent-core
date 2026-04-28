package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.SystemPromptKey;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

/**
 * prompt 渲染请求。
 */
@Value
@Builder(toBuilder = true)
public class PromptRenderRequest {

    /** 待渲染的系统 prompt key。 */
    SystemPromptKey promptKey;

    /** 本次渲染输入变量。 */
    @Singular("variable")
    Map<String, String> variables;
}
