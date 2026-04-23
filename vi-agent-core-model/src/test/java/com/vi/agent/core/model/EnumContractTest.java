package com.vi.agent.core.model;

import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.runtime.RunEventActorType;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.turn.TurnStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            assertNotNull(value, enumClass.getSimpleName() + "." + enumConstant.name() + " value");
            assertNotNull(desc, enumClass.getSimpleName() + "." + enumConstant.name() + " desc");
        }
    }
}
