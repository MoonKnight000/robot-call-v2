-- How a catalog voice sounds, for the voice picker's gender and age filters. Synced vendors
-- (ElevenLabs, Cartesia, Gemini) fill them from their own list API on every sync; the
-- hand-kept rows get their gender in R__seed_data.sql. NULL means nobody described the voice.
ALTER TABLE tts_voice ADD COLUMN gender VARCHAR(16)
    CONSTRAINT ck_tts_voice_gender CHECK (gender IN ('FEMALE', 'MALE', 'NEUTRAL'));
ALTER TABLE tts_voice ADD COLUMN age_group VARCHAR(16)
    CONSTRAINT ck_tts_voice_age_group CHECK (age_group IN ('YOUNG', 'MIDDLE_AGED', 'OLD'));
