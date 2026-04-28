Metadata:
- conversationId: {{conversationId}}
- sessionId: {{sessionId}}
- turnId: {{turnId}}
- runId: {{runId}}
- traceId: {{traceId}}
- agentMode: {{agentMode}}
- workingContextSnapshotId: {{workingContextSnapshotId}}

[BEGIN_UNTRUSTED_SESSION_STATE_JSON]
{{currentStateJson}}
[END_UNTRUSTED_SESSION_STATE_JSON]

[BEGIN_UNTRUSTED_CONVERSATION_SUMMARY]
{{conversationSummaryText}}
[END_UNTRUSTED_CONVERSATION_SUMMARY]

[BEGIN_UNTRUSTED_CURRENT_TURN_MESSAGES]
{{turnMessagesText}}
[END_UNTRUSTED_CURRENT_TURN_MESSAGES]
