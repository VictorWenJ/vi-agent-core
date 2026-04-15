package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.transcript.ConversationTranscript;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 1 简单上下文装配器：返回全量历史消息，不做 Token 裁剪。
 */
public class SimpleContextAssembler implements ContextAssembler {

    @Override
    public List<Message> assemble(ConversationTranscript transcript, UserMessage currentUserMessage) {
        List<Message> workingMessages = new ArrayList<>(transcript.getMessages());
        workingMessages.add(currentUserMessage);
        return workingMessages;
    }
}
