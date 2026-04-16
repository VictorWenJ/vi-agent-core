package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Chat Completion 响应体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiChatResponse {

    /**
     * 结果列表。
     */
    private List<ApiChoice> choices;
}
