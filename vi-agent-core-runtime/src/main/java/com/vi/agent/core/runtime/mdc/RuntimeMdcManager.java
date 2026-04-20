package com.vi.agent.core.runtime.mdc;

import com.vi.agent.core.model.runtime.RunMetadata;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime MDC manager.
 */
@Component
public class RuntimeMdcManager {

    public MdcScope open(String requestId, String conversationId, String sessionId, RunMetadata runMetadata) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.put("requestId", requestId);
        MDC.put("traceId", runMetadata.getTraceId());
        MDC.put("runId", runMetadata.getRunId());
        MDC.put("turnId", runMetadata.getTurnId());
        MDC.put("conversationId", conversationId);
        MDC.put("sessionId", sessionId);
        return new MdcScope(previous == null ? new HashMap<>() : previous);
    }
}
