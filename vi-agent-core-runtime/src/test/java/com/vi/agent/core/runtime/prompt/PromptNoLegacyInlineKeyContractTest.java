package com.vi.agent.core.runtime.prompt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-E4 旧 prompt key / version 防回退合同测试。
 */
class PromptNoLegacyInlineKeyContractTest {

    @Test
    void productionCodeShouldNotContainLegacyPromptKeysOrVersions() throws IOException {
        Path repositoryRoot = PromptContractTestSupport.repositoryRoot();
        Path runtimeMainSource = repositoryRoot.resolve("vi-agent-core-runtime/src/main/java");
        List<Path> productionJavaFiles = Files.walk(runtimeMainSource)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .toList();

        for (String forbiddenToken : forbiddenLegacyTokens()) {
            List<Path> offenders = productionJavaFiles.stream()
                .filter(path -> contains(path, forbiddenToken))
                .toList();
            assertTrue(offenders.isEmpty(), "生产源码仍包含旧 prompt key/version " + forbiddenToken + ": " + offenders);
        }
    }

    /**
     * 构造旧 token 列表，避免测试源码自身形成连续旧 key。
     */
    private List<String> forbiddenLegacyTokens() {
        return List.of(
            "state_" + "extract_inline",
            "summary_" + "extract_inline",
            "runtime" + "-instruction",
            "session" + "-state",
            "conversation" + "-summary",
            "p2" + "-c-v1",
            "p2" + "-d-2-v1",
            "p2" + "-d-3-v1",
            "p2" + "-d-4-v1"
        );
    }

    /**
     * 判断文件是否包含旧 token。
     */
    private boolean contains(Path path, String token) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(token);
        } catch (IOException ex) {
            throw new IllegalStateException("读取源码失败: " + path, ex);
        }
    }
}
