-- A campaign's target source can be one of the integration's tools (provider TOOL): which tool,
-- and the arguments it is called with.
ALTER TABLE campaign_target_source
    ADD COLUMN tool_name      VARCHAR(100),
    ADD COLUMN tool_arguments JSONB;
