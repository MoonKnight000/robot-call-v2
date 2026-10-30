-- Speech Languages for AI Agent (Retell-style single-select vs multilingual mode).
-- Default is empty array which resolves to [language].
ALTER TABLE ai_agent ADD COLUMN speech_languages JSONB NOT NULL DEFAULT '[]';

-- Backfill existing rows so they have their primary language in speech_languages
UPDATE ai_agent
SET speech_languages = jsonb_build_array(language)
WHERE speech_languages = '[]'::jsonb;
