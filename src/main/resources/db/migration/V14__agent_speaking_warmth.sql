-- How much warmth an agent's wording carries (RESERVED, BALANCED, WARM). The Uzbek Yandex
-- voices have no emotional roles, so the wording is where a call's warmth comes from.
-- Existing agents move to BALANCED: the old one-word style read as a machine on calls.
ALTER TABLE ai_agent ADD COLUMN speaking_warmth VARCHAR(16) NOT NULL DEFAULT 'BALANCED';
