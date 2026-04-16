package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 流式块响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiStreamResponse {

    /**
     * 流式 choice 列表。
     */
    private List<ApiStreamChoice> choices;
}
