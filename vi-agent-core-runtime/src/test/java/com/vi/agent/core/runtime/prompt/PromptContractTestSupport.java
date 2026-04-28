package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P2-E structured output 契约测试辅助方法。
 */
public final class PromptContractTestSupport {

    private PromptContractTestSupport() {
    }

    /**
     * 读取正式 app catalog 中的 state delta contract。
     */
    public static StructuredLlmOutputContract stateDeltaContract() {
        return StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .outputTarget(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT)
            .schemaJson(readContract("vi-agent-core-app", "state_delta_extract"))
            .description("State delta extraction structured output contract.")
            .build();
    }

    /**
     * 读取正式 app catalog 中的 conversation summary contract。
     */
    public static StructuredLlmOutputContract conversationSummaryContract() {
        return StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
            .outputTarget(StructuredLlmOutputTarget.CONVERSATION_SUMMARY_EXTRACTION_RESULT)
            .schemaJson(readContract("vi-agent-core-app", "conversation_summary_extract"))
            .description("Conversation summary extraction structured output contract.")
            .build();
    }

    /**
     * 读取测试 fixture 文本。
     */
    public static String fixture(String relativePath) {
        try (var inputStream = PromptContractTestSupport.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("测试 fixture 不存在: " + relativePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取测试 fixture 失败: " + relativePath, ex);
        }
    }

    /**
     * 读取工作区内的文件文本。
     */
    public static String readWorkspaceFile(String relativePath) {
        try {
            return Files.readString(repositoryRoot().resolve(relativePath), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取工作区文件失败: " + relativePath, ex);
        }
    }

    /**
     * 定位 Maven 多模块仓库根目录。
     */
    public static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("vi-agent-core-runtime"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录");
    }

    private static String readContract(String moduleName, String promptKey) {
        return readWorkspaceFile(moduleName + "/src/main/resources/prompt-catalog/system/"
            + promptKey + "/contract.json");
    }
}
