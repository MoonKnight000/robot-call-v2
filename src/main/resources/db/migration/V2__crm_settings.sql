-- Per-company CRM settings: which pipeline stage a call outcome moves the lead to, and which
-- CRM employee the AI's calls, tasks and leads are recorded under. Each company's funnel has
-- its own status ids, so these cannot be platform configuration (voice-agent.crm.*-status-id
-- stays only as the fallback for a company that saved nothing here).
CREATE TABLE crm_settings (
    id                        BIGSERIAL PRIMARY KEY,
    company_id                BIGINT      NOT NULL UNIQUE REFERENCES company(id) ON DELETE CASCADE,
    ai_employee_id            INTEGER,
    meeting_booked_status_id  INTEGER,
    promise_to_pay_status_id  INTEGER,
    not_interested_status_id  INTEGER,
    callback_status_id        INTEGER,
    new_lead_status_id        INTEGER,
    -- Uysot lead-task type the AI's follow-up tasks are created with (GET /v1/open-api/lead-task/types).
    task_type_id              INTEGER,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
