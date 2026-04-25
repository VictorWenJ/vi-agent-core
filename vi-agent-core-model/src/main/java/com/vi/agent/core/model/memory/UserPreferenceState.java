package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * 用户表达偏好状态。
 */
@Getter
@Builder
@Jacksonized
public class UserPreferenceState {

    /** 用户偏好的回答风格。 */
    private final AnswerStyle answerStyle;

    /** 用户偏好的细节程度。 */
    private final DetailLevel detailLevel;

    /** 用户偏好的术语格式。 */
    private final TermFormat termFormat;
}
