-- Every priced LLM charge, one row per model: what it spent and what that cost. A call that
-- ran on two models (agent + fast model) has two CALL rows, its summary a SUMMARY row, a
-- copilot reply a COPILOT row. The money is the same that reached the balance through the
-- ledger: this is the per-model explanation of it, not a second charge.
CREATE TABLE llm_usage (
    id                      BIGSERIAL    PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- LlmUsagePurpose: CALL, SUMMARY, ANALYSIS, REANALYSIS, CHAT, COPILOT, CALL_QUESTION
    purpose                 VARCHAR(24)  NOT NULL,
    -- What was charged, as the ledger names it (call_attempt, copilot_message, chat_session)
    reference_type          VARCHAR(32)  NOT NULL,
    reference_id            VARCHAR(64)  NOT NULL,
    -- The price-list id it was priced as, or the reported id when unlisted; NULL = unknown
    model                   VARCHAR(128),
    -- true = the model's own price (billing_model_price), false = the common rate in billing.yml
    listed                  BOOLEAN      NOT NULL,
    input_tokens            BIGINT       NOT NULL,
    output_tokens           BIGINT       NOT NULL,
    cached_tokens           BIGINT       NOT NULL,
    -- The prices used, per million, before the markup, so a row explains itself later
    input_per_million_uzs   BIGINT       NOT NULL,
    output_per_million_uzs  BIGINT       NOT NULL,
    cached_per_million_uzs  BIGINT       NOT NULL,
    markup_basis_points     INT          NOT NULL,
    -- With the markup, each rounded on its own
    input_uzs               BIGINT       NOT NULL,
    output_uzs              BIGINT       NOT NULL,
    cached_uzs              BIGINT       NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_usage_company_created ON llm_usage(company_id, created_at);
CREATE INDEX idx_llm_usage_reference ON llm_usage(reference_type, reference_id);
