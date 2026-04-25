package com.vi.agent.core.model.memory.statepatch;

import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.TermFormat;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Objects;

/**
 * 用户偏好的字段级补丁。
 *
 * <p>该对象只表达本轮显式更新的偏好字段，字段为 null 表示不修改对应旧值。</p>
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class UserPreferencePatch {

    /** 回答风格更新值，null 表示不修改。 */
    private final AnswerStyle answerStyle;

    /** 细节程度更新值，null 表示不修改。 */
    private final DetailLevel detailLevel;

    /** 术语表达格式更新值，null 表示不修改。 */
    private final TermFormat termFormat;

    /** 语言区域更新值，使用已有用户偏好模型中的 locale 规范，null 表示不修改。 */
    private final String locale;

    /** 时区更新值，使用已有用户偏好模型中的 timezone 规范，null 表示不修改。 */
    private final String timezone;

    /**
     * 判断该偏好补丁是否没有任何显式更新字段。
     *
     * @return 没有显式更新字段时返回 true
     */
    public boolean isEmpty() {
        return Objects.isNull(answerStyle)
            && Objects.isNull(detailLevel)
            && Objects.isNull(termFormat)
            && Objects.isNull(locale)
            && Objects.isNull(timezone);
    }
}
