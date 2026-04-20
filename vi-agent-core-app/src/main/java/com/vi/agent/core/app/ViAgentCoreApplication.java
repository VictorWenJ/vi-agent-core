package com.vi.agent.core.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * vi-agent-core 启动类。
 */
@SpringBootApplication(scanBasePackages = "com.vi.agent.core")
public class ViAgentCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViAgentCoreApplication.class, args);
    }
}
