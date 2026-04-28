package com.vi.agent.core.app.config;

import com.vi.agent.core.app.config.properties.SystemPromptProperties;
import com.vi.agent.core.infra.prompt.ResourceSystemPromptCatalogRepository;
import com.vi.agent.core.model.port.SystemPromptCatalogRepository;
import com.vi.agent.core.runtime.prompt.PromptRenderer;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistry;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

/**
 * Prompt Engineering Governance 装配配置。
 */
@Configuration
public class PromptGovernanceConfig {

    /** 本地 profile 名称集合。 */
    private static final Set<String> LOCAL_PROFILES = Set.of("local", "dev", "test");

    /** 系统 prompt catalog 配置。 */
    private final SystemPromptProperties systemPromptProperties;

    /** Spring 环境信息。 */
    private final Environment environment;

    /**
     * 构造 Prompt Governance 配置。
     */
    public PromptGovernanceConfig(SystemPromptProperties systemPromptProperties, Environment environment) {
        this.systemPromptProperties = systemPromptProperties;
        this.environment = environment;
    }

    /**
     * 装配 classpath 只读系统 prompt catalog 仓储。
     */
    @Bean
    public SystemPromptCatalogRepository systemPromptCatalogRepository() {
        return new ResourceSystemPromptCatalogRepository(systemPromptProperties.getCatalogBasePath(), normalizedCatalogRevision());
    }

    /**
     * 装配系统 prompt 注册表工厂。
     */
    @Bean
    public SystemPromptRegistryFactory systemPromptRegistryFactory(
        SystemPromptCatalogRepository systemPromptCatalogRepository
    ) {
        return new SystemPromptRegistryFactory(systemPromptCatalogRepository);
    }

    /**
     * 装配启动期加载完成的系统 prompt 只读注册表。
     */
    @Bean
    public SystemPromptRegistry systemPromptRegistry(SystemPromptRegistryFactory systemPromptRegistryFactory) {
        if (Boolean.TRUE.equals(systemPromptProperties.getFailFast())) {
            return systemPromptRegistryFactory.create();
        }
        return systemPromptRegistryFactory.create();
    }

    /**
     * 装配系统 prompt 渲染器。
     */
    @Bean
    public PromptRenderer promptRenderer(SystemPromptRegistry systemPromptRegistry) {
        return new PromptRenderer(systemPromptRegistry);
    }

    private String normalizedCatalogRevision() {
        String catalogRevision = systemPromptProperties.getCatalogRevision();
        if (catalogRevision != null && !catalogRevision.isBlank()) {
            return catalogRevision;
        }
        if (isNonLocalProfile()) {
            throw new IllegalStateException("生产或非本地 profile 下 vi.agent.system-prompt.catalog-revision 不能为空");
        }
        return "local-dev";
    }

    private boolean isNonLocalProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }
        return Arrays.stream(activeProfiles)
            .map(String::toLowerCase)
            .noneMatch(LOCAL_PROFILES::contains);
    }
}
