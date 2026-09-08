-- MCP: tools a company hosts somewhere else, offered to its agent during a call.
--
-- The `tool` table already covers "call this REST endpoint with these parameters", which
-- a company has to describe field by field. An MCP server describes its own tools, so a
-- company that already runs one gets them all by pasting a URL.

CREATE TABLE mcp_connection (
    id            BIGSERIAL    PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name          VARCHAR(128) NOT NULL,
    -- HTTPS only, and screened against internal addresses before every call.
    url           VARCHAR(500) NOT NULL,
    -- NONE or BEARER. A bearer token is not stored here: secret_key names a row in the
    -- company's secret store, so the credential lives in one place with the rest.
    auth_type     VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    secret_key    VARCHAR(128),
    -- PENDING -> CONNECTED, or AUTH_REQUIRED / ERROR / DISABLED.
    status        VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    -- How many tools the last refresh found, and how many of those the model is allowed
    -- to see. The second number is the one worth looking at: a server offering forty
    -- tools of which two are usable is a server that needs allow_writes turning on.
    tool_count    INT          NOT NULL DEFAULT 0,
    usable_tool_count INT      NOT NULL DEFAULT 0,
    -- Off by default: a tool the server does not mark read-only can change something at
    -- the other end, and a model on a live call is talkable-into things.
    allow_writes  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- The tool list as the last refresh read it, so a call builds its tools from a row
    -- instead of waiting on somebody else's server between the caller answering and the
    -- agent speaking.
    tools_json    JSONB,
    last_error    VARCHAR(500),
    refreshed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_mcp_connection_name UNIQUE (company_id, name)
);
CREATE INDEX idx_mcp_connection_company ON mcp_connection(company_id);

-- Which agents may use which connection. Not every agent should reach every tool: a
-- collections agent and a support agent share a company, not a toolbox.
CREATE TABLE ai_agent_mcp_connection (
    ai_agent_id       BIGINT NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    mcp_connection_id BIGINT NOT NULL REFERENCES mcp_connection(id) ON DELETE CASCADE,
    PRIMARY KEY (ai_agent_id, mcp_connection_id)
);

-- Every tool call, with what it cost. Somebody's server answering slowly is felt as
-- silence on a live call, so this is where that gets proved rather than argued about.
CREATE TABLE mcp_tool_execution (
    id                BIGSERIAL    PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    mcp_connection_id BIGINT       REFERENCES mcp_connection(id) ON DELETE SET NULL,
    call_attempt_id   BIGINT       REFERENCES call_attempt(id) ON DELETE SET NULL,
    tool_name         VARCHAR(128) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    latency_ms        INT          NOT NULL DEFAULT 0,
    -- Truncated on the way in. These are for reading a failure back, not for keeping a
    -- copy of everything a customer's server was told.
    args_preview      VARCHAR(500),
    result_preview    VARCHAR(500),
    error             VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_mcp_execution_company_created ON mcp_tool_execution(company_id, created_at DESC);
CREATE INDEX idx_mcp_execution_call ON mcp_tool_execution(call_attempt_id);
