package com.vi.agent.core.infra.persistence;

import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.port.TranscriptStore;

import java.time.Instant;
import java.util.Optional;

/**
 * TranscriptStore 适配实现。
 */
public class TranscriptStoreService implements TranscriptStore {

    /** Transcript 仓储接口。 */
    private final TranscriptRepository transcriptRepository;

    public TranscriptStoreService(TranscriptRepository transcriptRepository) {
        this.transcriptRepository = transcriptRepository;
    }

    @Override
    public Optional<ConversationTranscript> load(String sessionId) {
        return transcriptRepository.findBySessionId(sessionId)
            .map(this::toModel);
    }

    @Override
    public void save(ConversationTranscript transcript) {
        transcriptRepository.save(toEntity(transcript));
    }

    private ConversationTranscript toModel(ConversationTranscriptEntity entity) {
        ConversationTranscript transcript = new ConversationTranscript(entity.getSessionId());
        transcript.setTraceId(entity.getTraceId());
        transcript.setRunId(entity.getRunId());
        transcript.replaceMessages(entity.getMessages());
        transcript.replaceToolCalls(entity.getToolCalls());
        transcript.replaceToolResults(entity.getToolResults());
        return transcript;
    }

    private ConversationTranscriptEntity toEntity(ConversationTranscript transcript) {
        ConversationTranscriptEntity entity = new ConversationTranscriptEntity();
        entity.setSessionId(transcript.getSessionId());
        entity.setTraceId(transcript.getTraceId());
        entity.setRunId(transcript.getRunId());
        entity.setMessages(new java.util.ArrayList<>(transcript.getMessages()));
        entity.setToolCalls(new java.util.ArrayList<>(transcript.getToolCalls()));
        entity.setToolResults(new java.util.ArrayList<>(transcript.getToolResults()));
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
