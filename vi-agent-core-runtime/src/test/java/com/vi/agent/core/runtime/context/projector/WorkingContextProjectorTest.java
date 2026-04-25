package com.vi.agent.core.runtime.context.projector;

import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorkingContextProjectorTest {

    @Test
    void projectShouldCreateModelMessagesInMainAgentOrder() {
        Message recentMessage = ContextTestFixtures.recentUserMessage();
        WorkingContext context = ContextTestFixtures.context(List.of(
            ContextTestFixtures.runtimeBlock(),
            ContextTestFixtures.stateBlock(),
            ContextTestFixtures.summaryBlock(),
            ContextTestFixtures.recentMessagesBlock(List.of(recentMessage)),
            ContextTestFixtures.currentUserBlock()
        ), ContextTestFixtures.budget(50));

        WorkingContextProjection projection = new WorkingContextProjector(new FixedSyntheticMessageIdGenerator()).project(context);

        assertEquals("wctx-1", projection.getWorkingContextSnapshotId());
        assertEquals(5, projection.getModelMessages().size());
        assertInstanceOf(SystemMessage.class, projection.getModelMessages().get(0));
        assertInstanceOf(SummaryMessage.class, projection.getModelMessages().get(1));
        assertInstanceOf(SummaryMessage.class, projection.getModelMessages().get(2));
        assertSame(recentMessage, projection.getModelMessages().get(3));
        assertInstanceOf(UserMessage.class, projection.getModelMessages().get(4));
        assertEquals("ctxmsg-system-runtime_instruction-1", projection.getModelMessages().get(0).getMessageId());
        assertEquals("ctxmsg-summary-session_state-2", projection.getModelMessages().get(1).getMessageId());
        assertEquals("state render", projection.getModelMessages().get(1).getContentText());
        assertEquals("msg-current", projection.getModelMessages().get(4).getMessageId());
    }

    private static final class FixedSyntheticMessageIdGenerator extends SyntheticMessageIdGenerator {
        private int count;

        @Override
        public String newSyntheticMessageId(MessageRole role, ContextBlockType blockType) {
            count++;
            return "ctxmsg-" + role.getValue() + "-" + blockType.getValue() + "-" + count;
        }
    }
}
