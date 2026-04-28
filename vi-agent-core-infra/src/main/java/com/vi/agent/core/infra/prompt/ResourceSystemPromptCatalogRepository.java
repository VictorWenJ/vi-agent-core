package com.vi.agent.core.infra.prompt;

import com.vi.agent.core.model.port.SystemPromptCatalogRepository;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 classpath resource 的系统 prompt catalog 仓储实现。
 */
public class ResourceSystemPromptCatalogRepository implements SystemPromptCatalogRepository {

    /** catalog classpath 根路径。 */
    private final String catalogBasePath;

    /** catalog 修订标识。 */
    private final String catalogRevision;

    /** prompt 模板组装器注册表。 */
    private final PromptTemplateAssemblerRegistry assemblerRegistry;

    /** 系统 prompt 模板只读快照。 */
    private final Map<SystemPromptKey, AbstractPromptTemplate> templates;

    /** 结构化输出契约只读快照。 */
    private final Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts;

    /** 模板正文内容 hash。 */
    private final Map<SystemPromptKey, String> templateContentHashes;

    /** manifest 内容 hash。 */
    private final Map<SystemPromptKey, String> manifestContentHashes;

    /** contract 内容 hash。 */
    private final Map<StructuredLlmOutputContractKey, String> contractContentHashes;

    /**
     * 加载全部系统 prompt key 对应的 classpath catalog。
     */
    public ResourceSystemPromptCatalogRepository(String catalogBasePath, String catalogRevision) {
        this(catalogBasePath, catalogRevision, Arrays.asList(SystemPromptKey.values()));
    }

    /**
     * 加载指定系统 prompt key 对应的 classpath catalog。
     */
    public ResourceSystemPromptCatalogRepository(String catalogBasePath, String catalogRevision, List<SystemPromptKey> promptKeys) {
        this.catalogBasePath = normalizeBasePath(catalogBasePath);
        this.catalogRevision = catalogRevision;
        this.assemblerRegistry = new PromptTemplateAssemblerRegistry();

        CatalogSnapshot snapshot = loadCatalog(promptKeys);
        this.templates = Map.copyOf(snapshot.templates);
        this.contracts = Map.copyOf(snapshot.contracts);
        this.templateContentHashes = Map.copyOf(snapshot.templateContentHashes);
        this.manifestContentHashes = Map.copyOf(snapshot.manifestContentHashes);
        this.contractContentHashes = Map.copyOf(snapshot.contractContentHashes);
    }

    @Override
    public Optional<AbstractPromptTemplate> findTemplate(SystemPromptKey promptKey) {
        return Optional.ofNullable(templates.get(promptKey));
    }

    @Override
    public Optional<StructuredLlmOutputContract> findContract(StructuredLlmOutputContractKey contractKey) {
        return Optional.ofNullable(contracts.get(contractKey));
    }

    @Override
    public String templateContentHash(SystemPromptKey promptKey) {
        return templateContentHashes.get(promptKey);
    }

    @Override
    public String manifestContentHash(SystemPromptKey promptKey) {
        return manifestContentHashes.get(promptKey);
    }

    @Override
    public String contractContentHash(StructuredLlmOutputContractKey contractKey) {
        return contractContentHashes.get(contractKey);
    }

    @Override
    public String catalogRevision() {
        return catalogRevision;
    }

    private CatalogSnapshot loadCatalog(List<SystemPromptKey> promptKeys) {
        CatalogSnapshot snapshot = new CatalogSnapshot();
        for (SystemPromptKey promptKey : promptKeys) {
            loadPrompt(promptKey, snapshot);
        }
        return snapshot;
    }

    private void loadPrompt(SystemPromptKey expectedPromptKey, CatalogSnapshot snapshot) {
        String promptDirectory = catalogBasePath + "/" + expectedPromptKey.getValue();
        String manifestContent = readResource(promptDirectory + "/manifest.yml");
        PromptManifestLoader.PromptManifest manifest = PromptManifestLoader.loadManifest(manifestContent);
        validateManifestMapping(expectedPromptKey, manifest);
        validateInputVariables(manifest);

        if (manifest.renderOutputType() == PromptRenderOutputType.TEXT) {
            loadTextPrompt(expectedPromptKey, promptDirectory, manifest, manifestContent, snapshot);
            return;
        }
        loadChatMessagesPrompt(expectedPromptKey, promptDirectory, manifest, manifestContent, snapshot);
    }

    private void loadTextPrompt(
        SystemPromptKey expectedPromptKey,
        String promptDirectory,
        PromptManifestLoader.PromptManifest manifest,
        String manifestContent,
        CatalogSnapshot snapshot
    ) {
        if (manifest.structuredOutputContractKey() != null) {
            throw new IllegalStateException("TEXT prompt 不允许绑定结构化输出契约: " + expectedPromptKey.getValue());
        }
        String promptContent = readResource(promptDirectory + "/prompt.md");
        validatePlaceholders(promptContent, manifest);
        AbstractPromptTemplate template = assemblerRegistry.assembleText(
            manifest,
            PromptManifestLoader.normalizeContent(promptContent)
        );
        snapshot.templates.put(expectedPromptKey, template);
        snapshot.templateContentHashes.put(expectedPromptKey, PromptManifestLoader.sha256Hex(promptContent));
        snapshot.manifestContentHashes.put(expectedPromptKey, PromptManifestLoader.sha256Hex(manifestContent));
    }

    private void loadChatMessagesPrompt(
        SystemPromptKey expectedPromptKey,
        String promptDirectory,
        PromptManifestLoader.PromptManifest manifest,
        String manifestContent,
        CatalogSnapshot snapshot
    ) {
        if (manifest.structuredOutputContractKey() == null) {
            throw new IllegalStateException("CHAT_MESSAGES prompt 必须绑定结构化输出契约: " + expectedPromptKey.getValue());
        }
        String systemContent = readResource(promptDirectory + "/system.md");
        String userContent = readResource(promptDirectory + "/user.md");
        String contractContent = readResource(promptDirectory + "/contract.json");
        StructuredLlmOutputContract contract = PromptManifestLoader.loadContract(contractContent);
        if (contract.getStructuredOutputContractKey() != manifest.structuredOutputContractKey()) {
            throw new IllegalStateException("manifest 与 contract.json 的结构化输出契约 key 不一致: " + expectedPromptKey.getValue());
        }
        if (snapshot.contracts.containsKey(contract.getStructuredOutputContractKey())) {
            throw new IllegalStateException("重复的结构化输出契约 key: " + contract.getStructuredOutputContractKey().getValue());
        }

        String combinedTemplate = systemContent + "\n" + userContent;
        validatePlaceholders(combinedTemplate, manifest);
        validateUntrustedBoundaries(combinedTemplate, manifest);
        AbstractPromptTemplate template = assemblerRegistry.assembleChatMessages(
            manifest,
            PromptManifestLoader.normalizeContent(systemContent),
            PromptManifestLoader.normalizeContent(userContent)
        );

        snapshot.templates.put(expectedPromptKey, template);
        snapshot.contracts.put(contract.getStructuredOutputContractKey(), contract);
        snapshot.templateContentHashes.put(expectedPromptKey, PromptManifestLoader.sha256Hex(combinedTemplate));
        snapshot.manifestContentHashes.put(expectedPromptKey, PromptManifestLoader.sha256Hex(manifestContent));
        snapshot.contractContentHashes.put(
            contract.getStructuredOutputContractKey(),
            PromptManifestLoader.sha256Hex(contractContent)
        );
    }

    private void validateManifestMapping(SystemPromptKey expectedPromptKey, PromptManifestLoader.PromptManifest manifest) {
        if (manifest.promptKey() != expectedPromptKey) {
            throw new IllegalStateException("manifest.promptKey 与资源目录不一致: " + expectedPromptKey.getValue());
        }
        if (manifest.purpose() != fixedPurpose(expectedPromptKey)) {
            throw new IllegalStateException("manifest.purpose 与模板固定用途不一致: " + expectedPromptKey.getValue());
        }
        if (manifest.renderOutputType() != fixedRenderOutputType(expectedPromptKey)) {
            throw new IllegalStateException("manifest.renderOutputType 与模板固定输出形态不一致: " + expectedPromptKey.getValue());
        }
    }

    private void validateInputVariables(PromptManifestLoader.PromptManifest manifest) {
        Set<String> variableNames = new LinkedHashSet<>();
        for (PromptInputVariable variable : manifest.inputVariables()) {
            if (variable.getVariableName() == null || variable.getVariableName().isBlank()) {
                throw new IllegalStateException("prompt 输入变量名不能为空: " + manifest.promptKey().getValue());
            }
            if (!variableNames.add(variable.getVariableName())) {
                throw new IllegalStateException("prompt 输入变量重复: " + variable.getVariableName());
            }
            if (variable.getTrustLevel() == PromptInputTrustLevel.UNTRUSTED_DATA) {
                validateUntrustedInputVariable(variable);
            }
        }
    }

    private void validateUntrustedInputVariable(PromptInputVariable variable) {
        if (variable.getPlacement() == PromptInputPlacement.INSTRUCTION_BLOCK) {
            throw new IllegalStateException("UNTRUSTED_DATA 变量不得进入 instruction_block: " + variable.getVariableName());
        }
        if (variable.getMaxChars() == null) {
            throw new IllegalStateException("UNTRUSTED_DATA 变量必须声明 maxChars: " + variable.getVariableName());
        }
        if (variable.getTruncateMarker() == null || variable.getTruncateMarker().isBlank()) {
            throw new IllegalStateException("UNTRUSTED_DATA 变量必须声明 truncateMarker: " + variable.getVariableName());
        }
    }

    private void validatePlaceholders(
        String templateContent,
        PromptManifestLoader.PromptManifest manifest
    ) {
        Set<String> declaredNames = new LinkedHashSet<>();
        for (PromptInputVariable variable : manifest.inputVariables()) {
            declaredNames.add(variable.getVariableName());
        }
        for (String placeholder : PromptManifestLoader.findPlaceholders(templateContent)) {
            if (!declaredNames.contains(placeholder)) {
                throw new IllegalStateException("模板存在未声明占位符: " + placeholder);
            }
        }
    }

    private void validateUntrustedBoundaries(
        String templateContent,
        PromptManifestLoader.PromptManifest manifest
    ) {
        for (PromptInputVariable variable : manifest.inputVariables()) {
            if (variable.getTrustLevel() != PromptInputTrustLevel.UNTRUSTED_DATA) {
                continue;
            }
            String placeholder = "{{" + variable.getVariableName() + "}}";
            int searchFrom = 0;
            while (true) {
                int placeholderIndex = templateContent.indexOf(placeholder, searchFrom);
                if (placeholderIndex < 0) {
                    break;
                }
                int latestBegin = templateContent.lastIndexOf("[BEGIN_UNTRUSTED_", placeholderIndex);
                int latestEndBefore = templateContent.lastIndexOf("[END_UNTRUSTED_", placeholderIndex);
                int nextEnd = templateContent.indexOf("[END_UNTRUSTED_", placeholderIndex + placeholder.length());
                if (latestBegin < 0 || latestBegin < latestEndBefore || nextEnd < 0) {
                    throw new IllegalStateException("UNTRUSTED_DATA 变量必须位于成对边界内: " + variable.getVariableName());
                }
                searchFrom = placeholderIndex + placeholder.length();
            }
        }
    }

    private String readResource(String resourcePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("classpath prompt catalog 资源不存在: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 classpath prompt catalog 资源失败: " + resourcePath, ex);
        }
    }

    private String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalStateException("prompt catalog base path 不能为空");
        }
        String normalized = basePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private PromptPurpose fixedPurpose(SystemPromptKey promptKey) {
        return switch (promptKey) {
            case RUNTIME_INSTRUCTION_RENDER -> PromptPurpose.RUNTIME_INSTRUCTION_RENDER;
            case SESSION_STATE_RENDER -> PromptPurpose.SESSION_STATE_RENDER;
            case CONVERSATION_SUMMARY_RENDER -> PromptPurpose.CONVERSATION_SUMMARY_RENDER;
            case STATE_DELTA_EXTRACT -> PromptPurpose.STATE_DELTA_EXTRACTION;
            case CONVERSATION_SUMMARY_EXTRACT -> PromptPurpose.CONVERSATION_SUMMARY_EXTRACTION;
        };
    }

    private PromptRenderOutputType fixedRenderOutputType(SystemPromptKey promptKey) {
        return switch (promptKey) {
            case RUNTIME_INSTRUCTION_RENDER,
                SESSION_STATE_RENDER,
                CONVERSATION_SUMMARY_RENDER -> PromptRenderOutputType.TEXT;
            case STATE_DELTA_EXTRACT,
                CONVERSATION_SUMMARY_EXTRACT -> PromptRenderOutputType.CHAT_MESSAGES;
        };
    }

    /**
     * catalog 加载过程中的可变快照。
     */
    private static final class CatalogSnapshot {

        /** 系统 prompt 模板映射。 */
        private final Map<SystemPromptKey, AbstractPromptTemplate> templates = new LinkedHashMap<>();

        /** 结构化输出契约映射。 */
        private final Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts = new LinkedHashMap<>();

        /** 模板正文内容 hash 映射。 */
        private final Map<SystemPromptKey, String> templateContentHashes = new LinkedHashMap<>();

        /** manifest 内容 hash 映射。 */
        private final Map<SystemPromptKey, String> manifestContentHashes = new LinkedHashMap<>();

        /** contract 内容 hash 映射。 */
        private final Map<StructuredLlmOutputContractKey, String> contractContentHashes = new LinkedHashMap<>();
    }
}
