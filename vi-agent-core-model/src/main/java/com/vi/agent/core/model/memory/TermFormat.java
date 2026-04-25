package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 术语表达格式偏好。
 */
@Getter
@AllArgsConstructor
public enum TermFormat {

    ENGLISH_ONLY("englishOnly", "保留原文"),

    CHINESE_ONLY("chineseOnly", "翻译表达"),

    ENGLISH_ZH("englishZh", "双语表达");

    private final String value;

    private final String desc;
}
