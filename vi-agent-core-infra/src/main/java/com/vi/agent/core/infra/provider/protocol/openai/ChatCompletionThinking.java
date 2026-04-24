package com.vi.agent.core.infra.provider.protocol.openai;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class ChatCompletionThinking {
    private String type;
}
