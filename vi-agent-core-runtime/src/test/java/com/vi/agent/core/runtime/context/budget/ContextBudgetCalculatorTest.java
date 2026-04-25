package com.vi.agent.core.runtime.context.budget;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetCalculatorTest {

    @Test
    void calculateShouldKeepFineGrainedBudgetFields() {
        ContextBudgetCalculator calculator = new ContextBudgetCalculator(new ContextBudgetProperties(200, 20, 30, 10));

        ContextBudgetSnapshot snapshot = calculator.calculateForInputTokenEstimate(100);

        assertEquals(200, snapshot.getModelMaxInputTokens());
        assertEquals(100, snapshot.getInputTokenEstimate());
        assertEquals(20, snapshot.getReservedOutputTokens());
        assertEquals(30, snapshot.getReservedToolLoopTokens());
        assertEquals(10, snapshot.getSafetyMarginTokens());
        assertEquals(40, snapshot.getRemainingBudget());
        assertFalse(snapshot.getOverBudget());
    }

    @Test
    void calculateShouldMarkOverBudgetAndAllowNegativeRemainingBudget() {
        ContextBudgetCalculator calculator = new ContextBudgetCalculator(new ContextBudgetProperties(100, 20, 20, 10));

        ContextBudgetSnapshot snapshot = calculator.calculateForInputTokenEstimate(80);

        assertEquals(-30, snapshot.getRemainingBudget());
        assertTrue(snapshot.getOverBudget());
    }

    @Test
    void invalidConfigurationShouldFail() {
        assertThrows(AgentRuntimeException.class, () -> new ContextBudgetProperties(0, 20, 20, 10));
        assertThrows(AgentRuntimeException.class, () -> new ContextBudgetProperties(100, 50, 40, 10));
    }
}
