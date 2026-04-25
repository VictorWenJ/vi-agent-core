package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 术语表达格式偏好。
 */
@Getter
@AllArgsConstructor
public enum TermFormat {

    ORIGINAL("original", "保留原文"),

    TRANSLATED("translated", "翻译表达"),

    BILINGUAL("bilingual", "双语表达");

    private final String value;

    private final String desc;
}
