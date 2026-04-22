package com.vi.agent.core.model.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型提供方枚举。
 */
@Getter
@AllArgsConstructor
public enum ProviderType {

    /** DeepSeek。 */
    DEEPSEEK("deepseek", "DeepSeek"),

    /** OpenAI。 */
    OPENAI("openai", "OpenAI"),

    /** 豆包。 */
    DOUBAO("doubao", "豆包");

    private final String value;

    private final String desc;
}
