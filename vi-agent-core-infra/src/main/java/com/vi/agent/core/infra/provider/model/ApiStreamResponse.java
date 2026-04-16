package com.vi.agent.core.infra.provider.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ApiStreamResponse {

    /**
     * 候选列表。
     */
    private List<ApiStreamChoice> choices;
}
