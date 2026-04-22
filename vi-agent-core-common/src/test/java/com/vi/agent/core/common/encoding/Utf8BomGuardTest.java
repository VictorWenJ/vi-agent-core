package com.vi.agent.core.common.encoding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仓库文本文件 UTF-8 BOM 守卫测试。
 * <p>
 * 一旦有文本文件以 UTF-8 BOM 保存，测试直接失败，避免编译期出现“非法字符: '\\ufeff'”。
 */
class Utf8BomGuardTest {

    private static final byte UTF8_BOM_BYTE_1 = (byte) 0xEF;

    private static final byte UTF8_BOM_BYTE_2 = (byte) 0xBB;

    private static final byte UTF8_BOM_BYTE_3 = (byte) 0xBF;

    private static final Set<String> TEXT_SUFFIXES = Set.of(
        ".java",
        ".xml",
        ".yml",
        ".yaml",
        ".properties",
        ".sql",
        ".md"
    );

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
        ".git",
        ".idea",
        ".m2",
        "target",
        "out",
        "logs"
    );

    @Test
    void repositoryTextFilesShouldNotContainUtf8Bom() throws IOException {
        Path repositoryRoot = locateRepositoryRoot(Paths.get("").toAbsolutePath());
        List<String> filesWithBom = new ArrayList<>();

        try (Stream<Path> pathStream = Files.walk(repositoryRoot)) {
            pathStream
                .filter(Files::isRegularFile)
                .filter(this::isTextFile)
                .filter(path -> !isInExcludedDirectory(path))
                .forEach(path -> {
                    if (hasUtf8Bom(path)) {
                        filesWithBom.add(toUnixRelativePath(repositoryRoot, path));
                    }
                });
        }

        assertTrue(
            filesWithBom.isEmpty(),
            "检测到 UTF-8 BOM 文件，请改为 UTF-8（无 BOM）：" + System.lineSeparator() + String.join(System.lineSeparator(), filesWithBom)
        );
    }

    private boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return TEXT_SUFFIXES.stream().anyMatch(fileName::endsWith);
    }

    private boolean isInExcludedDirectory(Path path) {
        for (Path segment : path) {
            String segmentText = segment.toString();
            if (EXCLUDED_DIR_NAMES.contains(segmentText)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUtf8Bom(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            int b1 = inputStream.read();
            int b2 = inputStream.read();
            int b3 = inputStream.read();
            return b1 == (UTF8_BOM_BYTE_1 & 0xFF)
                && b2 == (UTF8_BOM_BYTE_2 & 0xFF)
                && b3 == (UTF8_BOM_BYTE_3 & 0xFF);
        } catch (IOException ignored) {
            return false;
        }
    }

    private String toUnixRelativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private Path locateRepositoryRoot(Path currentPath) {
        Path cursor = currentPath;
        while (cursor != null) {
            boolean hasRootPom = Files.exists(cursor.resolve("pom.xml"));
            boolean hasAgents = Files.exists(cursor.resolve("AGENTS.md"));
            boolean hasModules = Files.isDirectory(cursor.resolve("vi-agent-core-common"))
                && Files.isDirectory(cursor.resolve("vi-agent-core-model"))
                && Files.isDirectory(cursor.resolve("vi-agent-core-runtime"))
                && Files.isDirectory(cursor.resolve("vi-agent-core-infra"))
                && Files.isDirectory(cursor.resolve("vi-agent-core-app"));
            if (hasRootPom && hasAgents && hasModules) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录，请检查测试运行目录");
    }
}

