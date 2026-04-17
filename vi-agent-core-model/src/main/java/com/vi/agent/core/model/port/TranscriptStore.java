package com.vi.agent.core.model.port;

import com.vi.agent.core.model.transcript.ConversationTranscript;

import java.util.Optional;

/**
 * Transcript 存储接口。
 */
public interface TranscriptStore {

    /**
     * 按 sessionId 读取 Transcript。
     *
     * @param sessionId 会话 ID
     * @return 会话 Transcript
     */
    Optional<ConversationTranscript> load(String sessionId);

    /**
     * 保存 Transcript。
     *
     * @param transcript 会话 Transcript
     */
    void save(ConversationTranscript transcript);
}
