package com.vi.agent.core.model.port;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryOptionalContractTest {

    @Test
    void singleEntityRepositoryFindMethodsShouldReturnOptional() throws NoSuchMethodException {
        assertReturnType(MessageRepository.class, "findByMessageId", Optional.class, String.class);
        assertReturnType(MessageRepository.class, "findFinalAssistantMessageByTurnId", Optional.class, String.class);
        assertReturnType(TurnRepository.class, "findByRequestId", Optional.class, String.class);
        assertReturnType(TurnRepository.class, "findByTurnId", Optional.class, String.class);
        assertReturnType(SessionWorkingSetRepository.class, "findBySessionId", Optional.class, String.class);
    }

    private void assertReturnType(Class<?> repositoryType, String methodName, Class<?> expectedReturnType, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Method method = repositoryType.getDeclaredMethod(methodName, parameterTypes);
        assertEquals(expectedReturnType, method.getReturnType());
    }
}
