package com.vi.agent.core.app.api.config;

import com.vi.agent.core.infra.integration.mock.MockReadOnlyTools;
import com.vi.agent.core.runtime.tool.DefaultToolGateway;
import com.vi.agent.core.runtime.tool.ToolBundle;
import com.vi.agent.core.runtime.tool.ToolGateway;
import com.vi.agent.core.runtime.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class ToolConfig {

    @Bean
    public ToolBundle mockReadOnlyTools() {
        return new MockReadOnlyTools();
    }

    @Bean
    public ToolRegistry toolRegistry(List<ToolBundle> toolBundles) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerAnnotatedTools(toolBundles);
        return toolRegistry;
    }

    @Bean
    public ToolGateway toolGateway(ToolRegistry toolRegistry) {
        return new DefaultToolGateway(toolRegistry);
    }
}