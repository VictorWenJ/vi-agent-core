package com.vi.agent.core.runtime.context.policy;

import com.vi.agent.core.model.context.AgentMode;
import org.springframework.stereotype.Component;

/**
 * Resolves the active ContextPolicy. P2-C only enables GENERAL.
 */
@Component
public class ContextPolicyResolver {

    private final DefaultContextPolicy defaultContextPolicy;

    public ContextPolicyResolver(DefaultContextPolicy defaultContextPolicy) {
        this.defaultContextPolicy = defaultContextPolicy;
    }

    public ContextPolicy getPolicyByAgentMode(AgentMode agentMode) {
        return defaultContextPolicy;
    }
}
