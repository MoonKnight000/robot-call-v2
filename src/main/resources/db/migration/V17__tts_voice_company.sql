-- A company's own cloned voice (POST /api/tts/voices/clone). NULL = a shared catalog voice every
-- company may pick; set = only that company sees and speaks it. Never deleted (agents and calls
-- reference the id) — a removed clone is switched off.
ALTER TABLE tts_voice ADD COLUMN company_id BIGINT REFERENCES company(id);

CREATE INDEX idx_tts_voice_company ON tts_voice (company_id) WHERE company_id IS NOT NULL;
