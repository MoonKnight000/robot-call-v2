-- What one LLM / realtime model costs the company, per model, in UZS (markup NOT included —
-- the markup from billing.yml is applied on top, like every other provider cost).
-- A model missing here is priced at billing.yml's common LLM rate. Platform admins edit it
-- through /api/platform/model-prices.
CREATE TABLE billing_model_price (
    -- The model id as the agent / catalog names it, lower-case: gemini-2.5-flash, gpt-4o-mini.
    -- A provider's dated variant (gpt-4o-mini-2024-07-18, gemini-2.5-flash-preview-05-20)
    -- is matched to this row by ModelPriceMatcher.
    model                    VARCHAR(128) PRIMARY KEY,
    input_per_million_uzs    BIGINT       NOT NULL CHECK (input_per_million_uzs >= 0),
    output_per_million_uzs   BIGINT       NOT NULL CHECK (output_per_million_uzs >= 0),
    cached_per_million_uzs   BIGINT       NOT NULL CHECK (cached_per_million_uzs >= 0),
    -- Only for speech-to-speech engines (gemini-live, openai-realtime); NULL = billing.yml's realtime rate.
    realtime_per_minute_uzs  BIGINT       CHECK (realtime_per_minute_uzs >= 0),
    note                     VARCHAR(255),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
