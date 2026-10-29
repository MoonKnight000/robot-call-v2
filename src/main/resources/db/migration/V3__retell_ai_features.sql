-- Add sentiment auto-escalation fields to ai_agent table
ALTER TABLE ai_agent ADD COLUMN auto_escalate_on_frustration BOOLEAN DEFAULT FALSE;
ALTER TABLE ai_agent ADD COLUMN frustration_threshold INTEGER DEFAULT 2;

-- Create intent detection results table
CREATE TABLE IF NOT EXISTS call_intent (
    id BIGSERIAL PRIMARY KEY,
    call_attempt_id BIGINT NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    intent VARCHAR(100) NOT NULL,
    confidence DECIMAL(5,4),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (call_attempt_id) REFERENCES call_attempt(id)
);

-- Create event webhook triggers table
CREATE TABLE IF NOT EXISTS event_webhook_trigger (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    secret VARCHAR(255),
    headers TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create analytics metrics table
CREATE TABLE IF NOT EXISTS call_analytics (
    id BIGSERIAL PRIMARY KEY,
    call_attempt_id BIGINT NOT NULL,
    metric_type VARCHAR(100) NOT NULL,
    metric_key VARCHAR(255) NOT NULL,
    metric_value DECIMAL(20,4),
    metric_text TEXT,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (call_attempt_id) REFERENCES call_attempt(id)
);
