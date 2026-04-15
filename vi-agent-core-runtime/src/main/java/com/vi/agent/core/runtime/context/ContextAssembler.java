package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.transcript.ConversationTranscript;

import java.util.List;

/**
 * 上下文装配接口。
 */
public interface ContextAssembler {

    /**
     * 组装当前轮工作上下文。
     *
     * @param transcript 历史 Transcript
     * @param currentUserMessage 当前用户消息
     * @return 用于本轮推理的消息列表
     */
    List<Message> assemble(ConversationTranscript transcript, UserMessage currentUserMessage);
}
