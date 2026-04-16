package com.vi.agent.core.app.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Runtime 配置。
 */
@Getter
@Setter
public class RuntimeProperties {

    /** 最大迭代次数。 */
    private int maxIterations = 6;
}
