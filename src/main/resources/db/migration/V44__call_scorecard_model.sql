-- The model that judged an operator call, so its tokens are billed at that model's price
-- (billing_model_price) rather than the common rate. NULL for scorecards made before this.
ALTER TABLE call_scorecard ADD COLUMN model VARCHAR(128);
