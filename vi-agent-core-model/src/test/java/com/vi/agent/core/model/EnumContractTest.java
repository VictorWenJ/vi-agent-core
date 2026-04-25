package com.vi.agent.core.model;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointReason;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.EvidenceSourceType;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.MemoryWriteMode;
import com.vi.agent.core.model.memory.OpenLoopKind;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.TermFormat;
import com.vi.agent.core.model.memory.ToolOutcomeFreshnessPolicy;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.runtime.RunEventActorType;
import com.vi.agent.core.model.runtime.RunEventType;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.turn.TurnStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumContractTest {

    @Test
    void enumsShouldExposeNonBlankValueAndDesc() throws Exception {
        assertEnumContract(MessageRole.class);
        assertEnumContract(MessageType.class);
        assertEnumContract(RunStatus.class);
        assertEnumContract(TurnStatus.class);
        assertEnumContract(ToolCallStatus.class);
        assertEnumContract(ToolExecutionStatus.class);
        assertEnumContract(RunEventActorType.class);
        assertEnumContract(RunEventType.class);
        assertEnumContract(AgentMode.class);
        assertEnumContract(WorkingMode.class);
        assertEnumContract(ContextViewType.class);
        assertEnumContract(ContextBlockType.class);
        assertEnumContract(ContextPriority.class);
        assertEnumContract(CheckpointTrigger.class);
        assertEnumContract(CheckpointReason.class);
        assertEnumContract(ContextAssemblyDecision.class);
        assertEnumContract(AnswerStyle.class);
        assertEnumContract(DetailLevel.class);
        assertEnumContract(TermFormat.class);
        assertEnumContract(OpenLoopKind.class);
        assertEnumContract(OpenLoopStatus.class);
        assertEnumContract(ToolOutcomeFreshnessPolicy.class);
        assertEnumContract(MemoryWriteMode.class);
        assertEnumContract(EvidenceTargetType.class);
        assertEnumContract(EvidenceSourceType.class);
        assertEnumContract(InternalTaskType.class);
        assertEnumContract(InternalTaskStatus.class);
    }

    @Test
    void targetEnumsShouldUseChineseDesc() throws Exception {
        assertChineseDesc(RunEventActorType.class);
        assertChineseDesc(RunEventType.class);
        assertChineseDesc(ToolCallStatus.class);
        assertChineseDesc(ToolExecutionStatus.class);
        assertChineseDesc(AgentMode.class);
        assertChineseDesc(WorkingMode.class);
        assertChineseDesc(ContextViewType.class);
        assertChineseDesc(ContextBlockType.class);
        assertChineseDesc(ContextPriority.class);
        assertChineseDesc(CheckpointTrigger.class);
        assertChineseDesc(CheckpointReason.class);
        assertChineseDesc(ContextAssemblyDecision.class);
        assertChineseDesc(AnswerStyle.class);
        assertChineseDesc(DetailLevel.class);
        assertChineseDesc(TermFormat.class);
        assertChineseDesc(OpenLoopKind.class);
        assertChineseDesc(OpenLoopStatus.class);
        assertChineseDesc(ToolOutcomeFreshnessPolicy.class);
        assertChineseDesc(MemoryWriteMode.class);
        assertChineseDesc(EvidenceTargetType.class);
        assertChineseDesc(EvidenceSourceType.class);
        assertChineseDesc(InternalTaskType.class);
        assertChineseDesc(InternalTaskStatus.class);
    }

    @Test
    void runEventActorTypeShouldContainExpectedValuesOnly() {
        Set<String> expected = Set.of("USER", "MODEL", "TOOL", "AGENT", "SYSTEM");
        Set<String> actual = Arrays.stream(RunEventActorType.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual);
        assertThrows(IllegalArgumentException.class, () -> RunEventActorType.valueOf("ASSISTANT"));
        assertThrows(IllegalArgumentException.class, () -> RunEventActorType.valueOf("RUNTIME"));
    }

    @Test
    void messageTypeShouldNotContainToolCall() {
        assertThrows(IllegalArgumentException.class, () -> MessageType.valueOf("TOOL_CALL"));
        assertFalse(java.util.Arrays.stream(MessageType.values()).anyMatch(value -> "TOOL_CALL".equals(value.name())));
    }

    private static <E extends Enum<E>> void assertEnumContract(Class<E> enumClass) throws Exception {
        Method getValue = enumClass.getMethod("getValue");
        Method getDesc = enumClass.getMethod("getDesc");
        for (E enumConstant : enumClass.getEnumConstants()) {
            Object value = getValue.invoke(enumConstant);
            Object desc = getDesc.invoke(enumConstant);
            assertTrue(value instanceof String valueText && !valueText.isBlank(),
                enumClass.getSimpleName() + "." + enumConstant.name() + " value");
            assertTrue(desc instanceof String descText && !descText.isBlank(),
                enumClass.getSimpleName() + "." + enumConstant.name() + " desc");
        }
    }

    private static <E extends Enum<E>> void assertChineseDesc(Class<E> enumClass) throws Exception {
        Method getDesc = enumClass.getMethod("getDesc");
        for (E enumConstant : enumClass.getEnumConstants()) {
            Object desc = getDesc.invoke(enumConstant);
            assertTrue(desc instanceof String descText && containsChinese(descText),
                enumClass.getSimpleName() + "." + enumConstant.name() + " desc should contain Chinese");
        }
    }

    private static boolean containsChinese(String text) {
        return text != null && text.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }
}
