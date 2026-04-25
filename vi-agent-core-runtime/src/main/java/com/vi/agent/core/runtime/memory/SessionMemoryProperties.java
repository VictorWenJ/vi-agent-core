package com.vi.agent.core.runtime.memory;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Post-turn session memory update switches.
 */
@Getter
@Component
public class SessionMemoryProperties {

    @Value("${vi.agent.memory.post-turn-update-enabled:true}")
    private boolean postTurnUpdateEnabled = true;

    @Value("${vi.agent.memory.state-extraction-enabled:true}")
    private boolean stateExtractionEnabled = true;

    @Value("${vi.agent.memory.summary-update-enabled:true}")
    private boolean summaryUpdateEnabled = true;

    public SessionMemoryProperties() {
    }

    public SessionMemoryProperties(
        boolean postTurnUpdateEnabled,
        boolean stateExtractionEnabled,
        boolean summaryUpdateEnabled
    ) {
        this.postTurnUpdateEnabled = postTurnUpdateEnabled;
        this.stateExtractionEnabled = stateExtractionEnabled;
        this.summaryUpdateEnabled = summaryUpdateEnabled;
    }
}
