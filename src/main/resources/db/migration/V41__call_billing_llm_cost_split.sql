-- The LLM line split into what input (prompt), output (completion) and cached prompt tokens cost,
-- so the billing screen can show each at its own price. llm_cost_uzs stays the sum of the three.
ALTER TABLE call_billing
    ADD COLUMN llm_prompt_cost_uzs     BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN llm_completion_cost_uzs BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN llm_cached_cost_uzs     BIGINT NOT NULL DEFAULT 0;

-- Rows charged before this split: llm_cost_uzs is divided by each token kind's share of it at
-- the default price list (prompt 1900, completion 7600, cached 475 UZS per million — the markup
-- is the same factor on all three, so it cancels out). Completion takes the rounding remainder,
-- so the three still add up to llm_cost_uzs exactly.
WITH weights AS (
    SELECT id,
           llm_cost_uzs AS llm,
           GREATEST(prompt_tokens + summary_prompt_tokens + analysis_prompt_tokens
                    - cached_tokens - summary_cached_tokens - analysis_cached_tokens, 0) * 1900.0 AS w_prompt,
           (completion_tokens + summary_completion_tokens + analysis_completion_tokens) * 7600.0 AS w_completion,
           (cached_tokens + summary_cached_tokens + analysis_cached_tokens) * 475.0             AS w_cached
      FROM call_billing
     WHERE llm_cost_uzs > 0
), split AS (
    SELECT id,
           llm,
           CASE WHEN w_prompt + w_completion + w_cached > 0
                THEN ROUND(llm * w_prompt / (w_prompt + w_completion + w_cached)) ELSE 0 END AS prompt_uzs,
           CASE WHEN w_prompt + w_completion + w_cached > 0
                THEN ROUND(llm * w_cached / (w_prompt + w_completion + w_cached)) ELSE 0 END AS cached_uzs
      FROM weights
)
UPDATE call_billing cb
   SET llm_prompt_cost_uzs     = split.prompt_uzs,
       llm_cached_cost_uzs     = split.cached_uzs,
       llm_completion_cost_uzs = split.llm - split.prompt_uzs - split.cached_uzs
  FROM split
 WHERE cb.id = split.id;

-- The billing screen sums settled charges of one company over a date range.
CREATE INDEX idx_call_billing_company_settled ON call_billing(company_id, settled_at)
    WHERE status = 'SETTLED';
