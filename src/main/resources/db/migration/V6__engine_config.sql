-- Which speech engine a company's calls run on becomes a per-company setting.
--
-- Until now the STT and TTS providers were picked once for the whole process
-- (voice-agent.stt.provider / voice-agent.tts.provider) and every tenant on the
-- instance spoke through the same two. That is a deployment decision leaking into a
-- product one: which vendor recognizes and speaks a company's Uzbek is something that
-- company pays for and has an opinion about.
--
-- The row also carries `mode`, because the choice is not always "which STT plus which
-- TTS": a realtime (speech-to-speech) engine replaces both at once, so it cannot be
-- expressed as a provider id in either column. CASCADE reads stt_provider/tts_provider
-- and ignores realtime_provider; REALTIME does the reverse.
--
-- Every column stays nullable: null means "use the process default", exactly as in
-- ai_model_config. A company with no row here behaves as it did before this migration.
CREATE TABLE engine_config (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT NOT NULL UNIQUE REFERENCES company(id),
    mode              VARCHAR(20) NOT NULL DEFAULT 'CASCADE',
    stt_provider      VARCHAR(50),
    tts_provider      VARCHAR(50),
    realtime_provider VARCHAR(50),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- voice_settings.provider was the same idea in the wrong place, and dead besides:
-- TtsRouter resolved its provider once in its constructor and never read the column.
-- Anything a company did set there is carried over to its new home before the column
-- goes, so a tenant that had chosen a provider now actually gets it.
INSERT INTO engine_config (company_id, mode, tts_provider)
SELECT company_id, 'CASCADE', provider
FROM voice_settings
WHERE provider IS NOT NULL AND provider <> '';

ALTER TABLE voice_settings DROP COLUMN provider;
