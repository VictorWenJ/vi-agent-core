package com.vi.agent.core.model.memory;

import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * 最近 completed raw transcript 工作集快照。
 */
@Getter
@Builder
public class SessionWorkingSetSnapshot {

    /** 当前 session ID。 */
    private final String sessionId;

    /** 会话所属 conversation ID。 */
    private final String conversationId;

    /** working set 版本。 */
    private final Long workingSetVersion;

    /** working set 内 message ID 列表。 */
    @Singular("messageId")
    private final List<String> messageIds;

    /** working set 内消息对象列表。 */
    @Singular("message")
    private final List<Message> messages;

    /** working set 更新时间。 */
    private final Instant updatedAt;
}
