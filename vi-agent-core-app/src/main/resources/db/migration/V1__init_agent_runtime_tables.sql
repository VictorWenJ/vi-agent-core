CREATE TABLE IF NOT EXISTS agent_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'autoincrement primary key',
  conversation_id VARCHAR(64) NOT NULL COMMENT 'conversation id',
  title VARCHAR(255) DEFAULT NULL COMMENT 'conversation title',
  status VARCHAR(32) NOT NULL COMMENT 'ACTIVE/CLOSED/DELETED',
  active_session_id VARCHAR(64) DEFAULT NULL COMMENT 'current active session id',
  created_at DATETIME NOT NULL COMMENT 'created time',
  updated_at DATETIME NOT NULL COMMENT 'updated time',
  last_message_at DATETIME DEFAULT NULL COMMENT 'last message time',
  UNIQUE KEY uk_agent_conversation_conversation_id (conversation_id),
  KEY idx_agent_conversation_active_session (active_session_id),
  KEY idx_agent_conversation_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='frontend conversation window';

CREATE TABLE IF NOT EXISTS agent_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'autoincrement primary key',
  session_id VARCHAR(64) NOT NULL COMMENT 'session id',
  conversation_id VARCHAR(64) NOT NULL COMMENT 'conversation id',
  parent_session_id VARCHAR(64) DEFAULT NULL COMMENT 'parent session id',
  status VARCHAR(32) NOT NULL COMMENT 'ACTIVE/ARCHIVED/FAILED',
  archive_reason VARCHAR(255) DEFAULT NULL COMMENT 'archive reason',
  created_at DATETIME NOT NULL COMMENT 'created time',
  updated_at DATETIME NOT NULL COMMENT 'updated time',
  archived_at DATETIME DEFAULT NULL COMMENT 'archived time',
  UNIQUE KEY uk_agent_session_session_id (session_id),
  KEY idx_agent_session_conversation_id (conversation_id),
  KEY idx_agent_session_conversation_status (conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='runtime session under conversation';

CREATE TABLE IF NOT EXISTS agent_turn (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'autoincrement primary key',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn id',
  conversation_id VARCHAR(64) NOT NULL COMMENT 'conversation id',
  session_id VARCHAR(64) NOT NULL COMMENT 'session id',
  request_id VARCHAR(128) NOT NULL COMMENT 'client request id',
  run_id VARCHAR(64) NOT NULL COMMENT 'runtime run id',
  status VARCHAR(32) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED/CANCELLED',
  user_message_id VARCHAR(64) NOT NULL COMMENT 'user message id',
  assistant_message_id VARCHAR(64) DEFAULT NULL COMMENT 'assistant message id',
  finish_reason VARCHAR(32) DEFAULT NULL COMMENT 'finish reason',
  input_tokens INT DEFAULT NULL COMMENT 'input token count',
  output_tokens INT DEFAULT NULL COMMENT 'output token count',
  total_tokens INT DEFAULT NULL COMMENT 'total token count',
  provider VARCHAR(64) DEFAULT NULL COMMENT 'provider name',
  model VARCHAR(128) DEFAULT NULL COMMENT 'model name',
  error_code VARCHAR(64) DEFAULT NULL COMMENT 'error code',
  error_message VARCHAR(1024) DEFAULT NULL COMMENT 'error message',
  created_at DATETIME NOT NULL COMMENT 'created time',
  completed_at DATETIME DEFAULT NULL COMMENT 'completed time',
  UNIQUE KEY uk_agent_turn_turn_id (turn_id),
  UNIQUE KEY uk_agent_turn_request_id (request_id),
  KEY idx_agent_turn_session_id (session_id),
  KEY idx_agent_turn_conversation_id (conversation_id),
  KEY idx_agent_turn_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='single request execution turn';

CREATE TABLE IF NOT EXISTS agent_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'autoincrement primary key',
  message_id VARCHAR(64) NOT NULL COMMENT 'message id',
  conversation_id VARCHAR(64) NOT NULL COMMENT 'conversation id',
  session_id VARCHAR(64) NOT NULL COMMENT 'session id',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn id',
  role VARCHAR(32) NOT NULL COMMENT 'USER/ASSISTANT/TOOL/SYSTEM/SUMMARY',
  message_type VARCHAR(32) NOT NULL COMMENT 'USER_INPUT/ASSISTANT_OUTPUT/TOOL_CALL/TOOL_RESULT',
  sequence_no BIGINT NOT NULL COMMENT 'sequence in session',
  content MEDIUMTEXT COMMENT 'message content',
  created_at DATETIME NOT NULL COMMENT 'created time',
  UNIQUE KEY uk_agent_message_message_id (message_id),
  UNIQUE KEY uk_agent_message_session_sequence (session_id, sequence_no),
  KEY idx_agent_message_turn_id (turn_id),
  KEY idx_agent_message_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='append-only message fact';

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'autoincrement primary key',
  tool_call_id VARCHAR(64) NOT NULL COMMENT 'tool call id',
  conversation_id VARCHAR(64) NOT NULL COMMENT 'conversation id',
  session_id VARCHAR(64) NOT NULL COMMENT 'session id',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn id',
  message_id VARCHAR(64) NOT NULL COMMENT 'tool call message id',
  tool_name VARCHAR(128) NOT NULL COMMENT 'tool name',
  arguments_json MEDIUMTEXT NOT NULL COMMENT 'tool arguments json',
  sequence_no INT NOT NULL COMMENT 'tool call sequence in run',
  status VARCHAR(32) NOT NULL COMMENT 'REQUESTED/EXECUTED/FAILED',
  created_at DATETIME NOT NULL COMMENT 'created time',
  UNIQUE KEY uk_agent_tool_call_tool_call_id (tool_call_id),
  KEY idx_agent_tool_call_turn_id (turn_id),
  KEY idx_agent_tool_call_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tool call fact';

CREATE TABLE IF NOT EXISTS agent_tool_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'autoincrement primary key',
  tool_call_id VARCHAR(64) NOT NULL COMMENT 'tool call id',
  conversation_id VARCHAR(64) NOT NULL COMMENT 'conversation id',
  session_id VARCHAR(64) NOT NULL COMMENT 'session id',
  turn_id VARCHAR(64) NOT NULL COMMENT 'turn id',
  message_id VARCHAR(64) NOT NULL COMMENT 'tool result message id',
  tool_name VARCHAR(128) NOT NULL COMMENT 'tool name',
  success TINYINT(1) NOT NULL COMMENT 'success or not',
  output_json MEDIUMTEXT COMMENT 'tool output json',
  error_code VARCHAR(64) DEFAULT NULL COMMENT 'error code',
  error_message VARCHAR(1024) DEFAULT NULL COMMENT 'error message',
  duration_ms BIGINT DEFAULT NULL COMMENT 'tool duration ms',
  created_at DATETIME NOT NULL COMMENT 'created time',
  KEY idx_agent_tool_result_tool_call_id (tool_call_id),
  KEY idx_agent_tool_result_turn_id (turn_id),
  KEY idx_agent_tool_result_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tool result fact';
