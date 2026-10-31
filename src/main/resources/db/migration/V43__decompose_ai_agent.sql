-- 1. Create ai_agent_ambience table (1:1 with ai_agent)
CREATE TABLE ai_agent_ambience (
    ai_agent_id                   BIGINT PRIMARY KEY REFERENCES ai_agent(id) ON DELETE CASCADE,
    ambient_sound                 VARCHAR(30)      NOT NULL DEFAULT 'OFFICE',
    ambient_sound_volume          DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ambient_sound_fade_in_seconds DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    thinking_sound                VARCHAR(50)      NOT NULL DEFAULT 'OFF',
    thinking_sound_volume         DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    noise_cancellation_enabled    BOOLEAN          NOT NULL DEFAULT true,
    noise_cancellation_mode       VARCHAR(50)      NOT NULL DEFAULT 'BACKGROUND_NOISE_SUPPRESSION'
);

-- 2. Create ai_agent_handbook_rule table (relational enum collection)
CREATE TABLE ai_agent_handbook_rule (
    ai_agent_id BIGINT      NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    rule        VARCHAR(64) NOT NULL,
    PRIMARY KEY (ai_agent_id, rule)
);

-- 3. Create ai_agent_pronunciation_rule table (relational pronunciation dictionary)
CREATE TABLE ai_agent_pronunciation_rule (
    ai_agent_id    BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    word           VARCHAR(128) NOT NULL,
    replacement    VARCHAR(256) NOT NULL,
    case_sensitive BOOLEAN      NOT NULL DEFAULT false,
    language       VARCHAR(10)  NOT NULL DEFAULT '*'
);
CREATE INDEX idx_ai_agent_pronunciation_agent ON ai_agent_pronunciation_rule(ai_agent_id);

-- 4. Migrate ambience data from ai_agent
INSERT INTO ai_agent_ambience (
    ai_agent_id, ambient_sound, ambient_sound_volume, ambient_sound_fade_in_seconds,
    thinking_sound, thinking_sound_volume, noise_cancellation_enabled, noise_cancellation_mode
)
SELECT
    id,
    COALESCE(ambient_sound, 'OFFICE'),
    COALESCE(ambient_sound_volume, 1.0),
    COALESCE(ambient_sound_fade_in_seconds, 1.5),
    COALESCE(thinking_sound, 'OFF'),
    COALESCE(thinking_sound_volume, 1.0),
    COALESCE(noise_cancellation_enabled, true),
    COALESCE(noise_cancellation_mode, 'BACKGROUND_NOISE_SUPPRESSION')
FROM ai_agent
ON CONFLICT (ai_agent_id) DO NOTHING;

-- 5. Migrate handbook rules from JSONB array
INSERT INTO ai_agent_handbook_rule (ai_agent_id, rule)
SELECT a.id, r.val::text
FROM ai_agent a,
     LATERAL jsonb_array_elements_text(a.handbook) AS r(val)
WHERE a.handbook IS NOT NULL
  AND jsonb_typeof(a.handbook) = 'array'
  AND jsonb_array_length(a.handbook) > 0
ON CONFLICT (ai_agent_id, rule) DO NOTHING;

-- 6. Migrate pronunciation rules from JSONB array
INSERT INTO ai_agent_pronunciation_rule (ai_agent_id, word, replacement, case_sensitive, language)
SELECT
    a.id,
    COALESCE(r ->> 'word', ''),
    COALESCE(r ->> 'replacement', ''),
    COALESCE((r ->> 'caseSensitive')::boolean, false),
    COALESCE(r ->> 'language', '*')
FROM ai_agent a,
     LATERAL jsonb_array_elements(a.pronunciation_rules) AS r
WHERE a.pronunciation_rules IS NOT NULL
  AND jsonb_typeof(a.pronunciation_rules) = 'array'
  AND jsonb_array_length(a.pronunciation_rules) > 0
  AND COALESCE(r ->> 'word', '') <> '';

-- 7. Drop moved columns from ai_agent
ALTER TABLE ai_agent
    DROP COLUMN IF EXISTS ambient_sound,
    DROP COLUMN IF EXISTS ambient_sound_volume,
    DROP COLUMN IF EXISTS ambient_sound_fade_in_seconds,
    DROP COLUMN IF EXISTS thinking_sound,
    DROP COLUMN IF EXISTS thinking_sound_volume,
    DROP COLUMN IF EXISTS noise_cancellation_enabled,
    DROP COLUMN IF EXISTS noise_cancellation_mode,
    DROP COLUMN IF EXISTS handbook,
    DROP COLUMN IF EXISTS pronunciation_rules;
