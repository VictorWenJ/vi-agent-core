package com.vi.agent.core.runtime.prompt;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaValidatorDependencyContractTest {

    @Test
    void rootPomShouldManageNetworkntValidatorVersionInTwoXLine() {
        Document document = parsePom("pom.xml");

        Optional<Element> dependency = findDependency(document, "com.networknt", "json-schema-validator");

        assertTrue(dependency.isPresent());
        String managedVersion = textOf(dependency.get(), "version");
        if (managedVersion.startsWith("${") && managedVersion.endsWith("}")) {
            String propertyName = managedVersion.substring(2, managedVersion.length() - 1);
            managedVersion = textOf(document.getDocumentElement(), propertyName);
        }

        assertTrue(managedVersion.startsWith("2."));
    }

    @Test
    void runtimePomShouldDependOnValidatorWithoutVersion() {
        Document document = parsePom("vi-agent-core-runtime/pom.xml");

        Optional<Element> dependency = findDependency(document, "com.networknt", "json-schema-validator");

        assertTrue(dependency.isPresent());
        assertFalse(hasDirectChild(dependency.get(), "version"));
    }

    @Test
    void infraAndAppPomShouldNotCarryValidatorAsBusinessDependency() {
        assertFalse(findDependency(parsePom("vi-agent-core-infra/pom.xml"), "com.networknt", "json-schema-validator").isPresent());
        assertFalse(findDependency(parsePom("vi-agent-core-app/pom.xml"), "com.networknt", "json-schema-validator").isPresent());
    }

    @Test
    void guardShouldUseNetworkntValidatorInsteadOfHandWrittenFieldLists() {
        String source = PromptContractTestSupport.readWorkspaceFile(
            "vi-agent-core-runtime/src/main/java/com/vi/agent/core/runtime/prompt/StructuredLlmOutputContractGuard.java"
        );

        assertTrue(source.contains("com.networknt.schema.Schema"));
        assertTrue(source.contains("SchemaRegistry"));
        assertFalse(source.contains("ALLOWED_TOP_LEVEL_FIELDS"));
        assertFalse(source.contains("FORBIDDEN_FIELDS"));
    }

    private Document parsePom(String relativePath) {
        try {
            String xml = PromptContractTestSupport.readWorkspaceFile(relativePath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IllegalStateException("解析 POM 失败: " + relativePath, ex);
        }
    }

    private Optional<Element> findDependency(Document document, String groupId, String artifactId) {
        NodeList nodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element dependency
                && groupId.equals(textOf(dependency, "groupId"))
                && artifactId.equals(textOf(dependency, "artifactId"))) {
                return Optional.of(dependency);
            }
        }
        return Optional.empty();
    }

    private String textOf(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private boolean hasDirectChild(Element element, String tagName) {
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child && tagName.equals(child.getTagName())) {
                return true;
            }
        }
        return false;
    }
}
