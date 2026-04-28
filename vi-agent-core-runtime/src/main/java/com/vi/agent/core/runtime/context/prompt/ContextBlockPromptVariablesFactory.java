package com.vi.agent.core.runtime.context.prompt;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * context block prompt 渲染变量工厂。
 */
@Component
public class ContextBlockPromptVariablesFactory {

    /**
     * 构造运行时指令渲染变量。
     */
    public Map<String, String> runtimeInstructionVariables(AgentMode agentMode, SessionStateSnapshot latestState) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("agentMode", enumValue(agentMode, AgentMode.GENERAL.getValue()));
        variables.put("workingMode", enumValue(latestState == null ? null : latestState.getWorkingMode(), WorkingMode.GENERAL_CONVERSATION.getValue()));
        variables.put("phaseStateText", latestState == null || latestState.getPhaseState() == null ? "" : JsonUtils.toJson(latestState.getPhaseState()));
        return variables;
    }

    /**
     * 构造会话状态 context block 渲染变量。
     */
    public Map<String, String> sessionStateVariables(SessionStateSnapshot stateSnapshot, String sessionStateText) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("stateVersion", stateSnapshot == null || stateSnapshot.getStateVersion() == null
            ? "0"
            : String.valueOf(stateSnapshot.getStateVersion()));
        variables.put("sessionStateText", StringUtils.defaultString(sessionStateText));
        return variables;
    }

    /**
     * 构造会话摘要 context block 渲染变量。
     */
    public Map<String, String> conversationSummaryVariables(ConversationSummary summary) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("summaryVersion", summary == null || summary.getSummaryVersion() == null
            ? "0"
            : String.valueOf(summary.getSummaryVersion()));
        variables.put("summaryText", summary == null ? "" : StringUtils.defaultString(summary.getSummaryText()));
        return variables;
    }

    /**
     * 读取枚举的稳定 value。
     */
    private String enumValue(AgentMode value, String defaultValue) {
        return value == null ? defaultValue : value.getValue();
    }

    /**
     * 读取枚举的稳定 value。
     */
    private String enumValue(WorkingMode value, String defaultValue) {
        return value == null ? defaultValue : value.getValue();
    }
}
