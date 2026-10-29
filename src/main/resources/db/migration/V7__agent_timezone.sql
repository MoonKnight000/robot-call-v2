-- The agent's own time zone (studio "Current Time Awareness"): the date and hour the prompt
-- states and the dates of remembered calls are read in it. NULL keeps the platform's zone,
-- Asia/Tashkent, so every existing agent behaves as before.
ALTER TABLE ai_agent ADD COLUMN timezone VARCHAR(64);
