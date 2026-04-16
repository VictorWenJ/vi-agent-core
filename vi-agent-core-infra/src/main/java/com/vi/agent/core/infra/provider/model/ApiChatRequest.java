package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiChatRequest {

    /** 模型名称。 */
    private String model;

    /** 输入消息列表。 */
    private List<ApiMessage> messages;

    /** 工具定义列表。 */
    private List<ApiToolDefinition> tools;

    /** 工具选择策略。 */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /** 是否流式。 */
    private boolean stream;
}
