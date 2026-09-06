-- Cross-call memory per client: one row per (company, phone), read into the system
-- prompt before every call and written back after every summarized conversation.
CREATE TABLE client_memory (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id),
    phone              VARCHAR(20)  NOT NULL,
    preferred_name     VARCHAR(100),
    preferred_language VARCHAR(10),
    operator_notes     TEXT,
    recent_calls       JSONB        NOT NULL DEFAULT '[]',
    facts              JSONB        NOT NULL DEFAULT '{}',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_client_memory_company_phone ON client_memory(company_id, phone);

-- Carry over what operators had typed into campaign_target.context_data while memory
-- lived there. One target per (company, phone) wins: the most recently created.
INSERT INTO client_memory (company_id, phone, preferred_name, operator_notes, recent_calls)
SELECT DISTINCT ON (t.company_id, t.phone)
       t.company_id,
       t.phone,
       NULLIF(t.context_data ->> 'preferredName', ''),
       NULLIF(t.context_data ->> 'operatorNotes', ''),
       CASE WHEN NULLIF(t.context_data ->> 'lastCallSummary', '') IS NULL THEN '[]'::jsonb
            ELSE jsonb_build_array(jsonb_build_object(
                    'at', to_char(t.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                    'scenarioKey', NULL,
                    'disposition', NULL,
                    'summary', t.context_data ->> 'lastCallSummary'))
       END
FROM campaign_target t
WHERE t.phone ~ '^\+?[0-9]+$'
  AND (t.context_data ? 'preferredName' OR t.context_data ? 'operatorNotes' OR t.context_data ? 'lastCallSummary')
ORDER BY t.company_id, t.phone, t.created_at DESC;
