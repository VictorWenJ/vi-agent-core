package com.vi.agent.core.runtime.context.render;

import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.PhaseState;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.UserPreferenceState;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Renders session state snapshot into stable, compact context text.
 */
@Component
public class SessionStateBlockRenderer {

    public String render(SessionStateSnapshot stateSnapshot) {
        if (stateSnapshot == null) {
            return "Session state: None.";
        }

        StringBuilder builder = new StringBuilder();
        appendLine(builder, "Session state:");
        appendLine(builder, "- Task goal: " + display(stateSnapshot.getTaskGoal()));
        appendLine(builder, "- Working mode: " + enumName(stateSnapshot.getWorkingMode()));
        appendBlankLine(builder);

        appendUserPreferences(builder, stateSnapshot.getUserPreferences());
        appendIndexedSection(builder, "Confirmed facts:", stateSnapshot.getConfirmedFacts(), ConfirmedFactRecord::getContent);
        appendIndexedSection(builder, "Active constraints:", stateSnapshot.getConstraints(), ConstraintRecord::getContent);
        appendIndexedSection(builder, "Decisions:", stateSnapshot.getDecisions(), this::renderDecision);
        appendIndexedSection(builder, "Open loops:", stateSnapshot.getOpenLoops(), this::renderOpenLoop);
        appendIndexedSection(builder, "Recent tool outcomes:", stateSnapshot.getRecentToolOutcomes(), this::renderToolOutcome);
        appendPhaseState(builder, stateSnapshot.getPhaseState());

        return builder.toString().trim();
    }

    private void appendUserPreferences(StringBuilder builder, UserPreferenceState preferences) {
        appendLine(builder, "User preferences:");
        appendLine(builder, "- Answer style: " + enumName(preferences == null ? null : preferences.getAnswerStyle()));
        appendLine(builder, "- Detail level: " + enumName(preferences == null ? null : preferences.getDetailLevel()));
        appendLine(builder, "- Term format: " + enumName(preferences == null ? null : preferences.getTermFormat()));
        appendBlankLine(builder);
    }

    private <T> void appendIndexedSection(
        StringBuilder builder,
        String sectionTitle,
        List<T> values,
        Function<T, String> renderer
    ) {
        appendLine(builder, sectionTitle);
        if (CollectionUtils.isEmpty(values)) {
            appendLine(builder, "- None");
            appendBlankLine(builder);
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            appendLine(builder, (i + 1) + ". " + display(renderer.apply(values.get(i))));
        }
        appendBlankLine(builder);
    }

    private String renderDecision(DecisionRecord decision) {
        if (decision == null) {
            return null;
        }
        return display(decision.getContent())
            + " [decidedBy=" + display(decision.getDecidedBy())
            + ", decidedAt=" + display(decision.getDecidedAt() == null ? null : decision.getDecidedAt().toString())
            + ", confidence=" + display(decision.getConfidence() == null ? null : decision.getConfidence().toString())
            + "]";
    }

    private String renderOpenLoop(OpenLoop openLoop) {
        if (openLoop == null) {
            return null;
        }
        return "[" + enumName(openLoop.getStatus()) + "] "
            + enumName(openLoop.getKind()) + " - " + display(openLoop.getContent())
            + " [loopId=" + display(openLoop.getLoopId())
            + ", sourceType=" + display(openLoop.getSourceType())
            + ", sourceRef=" + display(openLoop.getSourceRef())
            + ", createdAt=" + display(openLoop.getCreatedAt() == null ? null : openLoop.getCreatedAt().toString())
            + ", closedAt=" + display(openLoop.getClosedAt() == null ? null : openLoop.getClosedAt().toString())
            + "]";
    }

    private String renderToolOutcome(ToolOutcomeDigest toolOutcome) {
        if (toolOutcome == null) {
            return null;
        }
        return display(toolOutcome.getToolName()) + " - " + display(toolOutcome.getSummary())
            + " [freshnessPolicy=" + enumName(toolOutcome.getFreshnessPolicy())
            + ", validUntil=" + display(toolOutcome.getValidUntil() == null ? null : toolOutcome.getValidUntil().toString())
            + ", lastVerifiedAt=" + display(toolOutcome.getLastVerifiedAt() == null ? null : toolOutcome.getLastVerifiedAt().toString())
            + "]";
    }

    private void appendPhaseState(StringBuilder builder, PhaseState phaseState) {
        appendLine(builder, "Phase state:");
        appendLine(builder, "- promptEngineeringEnabled: " + booleanValue(phaseState == null ? null : phaseState.getPromptEngineeringEnabled()));
        appendLine(builder, "- contextAuditEnabled: " + booleanValue(phaseState == null ? null : phaseState.getContextAuditEnabled()));
        appendLine(builder, "- summaryEnabled: " + booleanValue(phaseState == null ? null : phaseState.getSummaryEnabled()));
        appendLine(builder, "- stateExtractionEnabled: " + booleanValue(phaseState == null ? null : phaseState.getStateExtractionEnabled()));
        appendLine(builder, "- compactionEnabled: " + booleanValue(phaseState == null ? null : phaseState.getCompactionEnabled()));
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private void appendBlankLine(StringBuilder builder) {
        builder.append('\n');
    }

    private String enumName(Enum<?> value) {
        return value == null ? "Not set" : value.name();
    }

    private String booleanValue(Boolean value) {
        return value == null ? "Not set" : value.toString();
    }

    private String display(String value) {
        return StringUtils.defaultIfBlank(value, "Not set");
    }
}
