-- A test case can answer the agent's company tools with canned responses in a TEXT
-- simulation (no request leaves), and can require tools to be called.
ALTER TABLE agent_test_case
    ADD COLUMN tool_mocks   JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN expect_tools JSONB NOT NULL DEFAULT '[]';

-- A run plays every case repeat_count times; each attempt is its own result row.
ALTER TABLE agent_eval_run
    ADD COLUMN repeat_count INT NOT NULL DEFAULT 1;

ALTER TABLE agent_eval_result
    ADD COLUMN attempt INT NOT NULL DEFAULT 1;
