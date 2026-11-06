-- 300 was written into every agent as a default, not chosen. It held the thinking tokens too, so a turn
-- that thought came back empty, and it hid the dialog reply limit (voice-agent.dialog.reply-limit),
-- which only applies when neither the agent nor the company sets a cap. NULL hands it back to them.
UPDATE ai_agent SET max_output_tokens = NULL WHERE max_output_tokens = 300;
