package com.vi.agent.core.app.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 系统 prompt catalog 配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vi.agent.system-prompt")
public class SystemPromptProperties {

    /** 是否在启动期 fail-fast 加载系统 prompt catalog。 */
    private Boolean failFast = true;

    /** 系统 prompt catalog 的 classpath 根路径。 */
    private String catalogBasePath = "prompt-catalog/system";

    /** 系统 prompt catalog 修订标识。 */
    private String catalogRevision = "local-dev";
}
