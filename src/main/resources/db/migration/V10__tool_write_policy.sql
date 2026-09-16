-- UNIVERSAL-OPERATOR-PLAN B.1 / B.3 / B.6: nested request bodies, a code-level policy for
-- tools that write, and a log of every request a company tool sends.

-- B.1: a JSON Schema for a body with nested objects and arrays, next to the flat api_body.
ALTER TABLE tool ADD COLUMN body_schema JSONB;

-- B.3: what a call to the tool does. Existing tools are sorted by method: a GET reads,
-- anything else is taken to write. DESTRUCTIVE is only ever set by hand. The default is a
-- net for rows written by plain SQL; the application always sets the column itself.
ALTER TABLE tool ADD COLUMN effect VARCHAR(16) NOT NULL DEFAULT 'WRITE';
UPDATE tool SET effect = 'READ' WHERE api_method = 'GET';
ALTER TABLE tool ADD CONSTRAINT ck_tool_effect CHECK (effect IN ('READ', 'WRITE', 'DESTRUCTIVE'));

-- Off for every existing tool, so a call that works today keeps working the same way; a
-- WRITE tool created from now on gets it switched on unless its author says otherwise.
ALTER TABLE tool ADD COLUMN requires_confirmation BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE tool ADD COLUMN readback_template VARCHAR(500);

-- B.6: one row per request a company tool sent. The arguments are kept as a hash — they
-- are the caller's name, phone and address, and telling equal requests apart is all this
-- log needs. A refused request keeps the start of the endpoint's answer.
CREATE TABLE tool_execution (
    id              BIGSERIAL    PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    tool_id         BIGINT       REFERENCES tool(id) ON DELETE SET NULL,
    call_attempt_id BIGINT       REFERENCES call_attempt(id) ON DELETE SET NULL,
    tool_name       VARCHAR(100) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    http_status     INT,
    latency_ms      INT          NOT NULL DEFAULT 0,
    args_hash       VARCHAR(64),
    error           VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_tool_execution_status CHECK (status IN ('OK', 'HTTP_ERROR', 'FAILED'))
);
CREATE INDEX idx_tool_execution_company_created ON tool_execution(company_id, created_at DESC);
CREATE INDEX idx_tool_execution_tool_created ON tool_execution(tool_id, created_at DESC);
CREATE INDEX idx_tool_execution_call ON tool_execution(call_attempt_id);
