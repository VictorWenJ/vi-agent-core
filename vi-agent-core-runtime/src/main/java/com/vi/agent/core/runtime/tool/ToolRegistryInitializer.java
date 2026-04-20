package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.model.tool.ToolBundle;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes tool registry from spring-managed tool bundles.
 */
@Component
public class ToolRegistryInitializer {

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private ApplicationContext applicationContext;

    @PostConstruct
    public void initialize() {
        List<ToolBundle> toolBundles = applicationContext.getBeansOfType(ToolBundle.class)
            .values()
            .stream()
            .toList();
        toolRegistry.registerAnnotatedTools(toolBundles);
    }
}
