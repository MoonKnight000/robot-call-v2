-- UNIVERSAL-OPERATOR-PLAN D.1: the rest of the signals read without an LLM, counted by the
-- dialog while the call runs. Their own migration rather than an edit of V12, which may
-- already be applied somewhere.
ALTER TABLE call_quality ADD COLUMN barge_in_count        INT NOT NULL DEFAULT 0;
ALTER TABLE call_quality ADD COLUMN no_input_prompt_count INT NOT NULL DEFAULT 0;
ALTER TABLE call_quality ADD COLUMN low_confidence_count  INT NOT NULL DEFAULT 0;
