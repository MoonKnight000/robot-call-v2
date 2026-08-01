-- Technical detail tab (§10.5 "Texnik" tab): channel/trunk, AMD result, STT/TTS/LLM
-- identity, token usage, and turn-latency stats, one row per call. Written once at call
-- teardown (CallFinalizer), read alongside call_attempt/call_result for CallDetail.
CREATE TABLE call_technical (
    call_id             BIGINT PRIMARY KEY REFERENCES call_attempt(id),
    channel_name        VARCHAR(255),
    trunk                VARCHAR(100),
    amd_result           VARCHAR(20),   -- MACHINE | HUMAN | NULL (AMD disabled for the call)
    stt_provider          VARCHAR(50),
    tts_provider          VARCHAR(50),
    tts_voice             VARCHAR(50),
    llm_model             VARCHAR(100),
    prompt_tokens         INT,
    completion_tokens     INT,
    cached_tokens         INT,
    turn_count            INT,
    avg_turn_latency_ms   INT,
    max_turn_latency_ms   INT,
    avg_llm_latency_ms    INT,
    max_llm_latency_ms    INT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Audit trail IP column (§10.13) — captured from the HTTP request at record() time;
-- NULL for actions that ran outside a request (the dialer's own scheduled work).
ALTER TABLE audit_log ADD COLUMN ip_address VARCHAR(45);
