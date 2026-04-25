package com.vi.agent.core.runtime.context.mode;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the runtime agent mode from command metadata.
 */
@Slf4j
@Component
public class AgentModeResolver {

    private static final String AGENT_MODE_METADATA_KEY = "agentMode";

    public AgentMode resolve(RuntimeExecuteCommand command) {
        if (command == null) {
            return AgentMode.GENERAL;
        }
        Map<String, Object> metadata = command.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return AgentMode.GENERAL;
        }
        Object rawAgentMode = metadata.get(AGENT_MODE_METADATA_KEY);
        if (rawAgentMode == null || StringUtils.isBlank(String.valueOf(rawAgentMode))) {
            return AgentMode.GENERAL;
        }
        String value = String.valueOf(rawAgentMode).trim();
        if (StringUtils.equalsIgnoreCase(AgentMode.GENERAL.name(), value)
            || StringUtils.equalsIgnoreCase(AgentMode.GENERAL.getValue(), value)) {
            return AgentMode.GENERAL;
        }
        log.warn("unsupported agentMode metadata value={}, fallback={}", value, AgentMode.GENERAL);
        return AgentMode.GENERAL;
    }
}
