package com.vi.agent.core.infra.provider.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepSeekPropertiesTest {

    @Test
    void defaultDeepSeekPropertiesShouldUseNormalEndpointAndDisableStrictToolCall() {
        DeepSeekProperties properties = new DeepSeekProperties();

        assertEquals("https://api.deepseek.com", properties.getBaseUrl());
        assertFalse(properties.getStrictToolCallEnabled());
    }
}
