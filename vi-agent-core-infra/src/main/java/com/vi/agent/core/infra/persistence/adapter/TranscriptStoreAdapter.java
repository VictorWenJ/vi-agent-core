package com.vi.agent.core.infra.persistence.adapter;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.persistence.mapper.RedisTranscriptMapper;
import com.vi.agent.core.infra.persistence.repository.TranscriptRepository;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.port.TranscriptStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * TranscriptStore 适配实现。
 */
@Slf4j
@RequiredArgsConstructor
public class TranscriptStoreAdapter implements TranscriptStore {

    /** Transcript 仓储接口。 */
    private final TranscriptRepository transcriptRepository;

    /** Transcript 映射器。 */
    private final RedisTranscriptMapper redisTranscriptMapper;

    @Override
    public Optional<ConversationTranscript> load(String sessionId) {
        try {
            log.info("TranscriptStore load sessionId={}", sessionId);
            return transcriptRepository.findBySessionId(sessionId).map(redisTranscriptMapper::toModel);
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.TRANSCRIPT_STORE_FAILED, "加载 transcript 失败", e);
        }
    }

    @Override
    public void save(ConversationTranscript transcript) {
        try {
            log.info("TranscriptStore save sessionId={} conversationId={} messages={} toolCalls={} toolResults={}",
                transcript.getSessionId(),
                transcript.getConversationId(),
                transcript.getMessages().size(),
                transcript.getToolCalls().size(),
                transcript.getToolResults().size());
            transcriptRepository.save(redisTranscriptMapper.toEntity(transcript));
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.TRANSCRIPT_STORE_FAILED, "保存 transcript 失败", e);
        }
    }
}
