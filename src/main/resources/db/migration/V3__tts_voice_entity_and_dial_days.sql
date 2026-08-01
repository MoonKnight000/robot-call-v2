-- TTS voice catalog becomes a real table (PROJECT.md §2.5) instead of
-- voice-agent.tts.catalog config — same reasoning as `scenario`: selectable data an
-- operator picks from belongs in the DB, not in a YAML file redeployed to change it.
CREATE TABLE tts_voice (
    id       VARCHAR(64)  PRIMARY KEY,   -- stable id stored on campaign.tts_voice, e.g. 'nigora'
    provider VARCHAR(50)  NOT NULL,      -- yandex | google
    language VARCHAR(10)  NOT NULL,      -- BCP-47, e.g. uz-UZ
    name     VARCHAR(100) NOT NULL,      -- provider-side voice name sent with synthesis
    label    VARCHAR(255) NOT NULL       -- human-readable name for the campaign form
);

INSERT INTO tts_voice (id, provider, language, name, label) VALUES
    ('nigora', 'yandex', 'uz-UZ', 'nigora', 'Nigora — o''zbek, ayol'),
    ('alena',  'yandex', 'ru-RU', 'alena',  'Alena — rus, ayol'),
    ('jane',   'yandex', 'ru-RU', 'jane',   'Jane — rus, ayol'),
    ('omazh',  'yandex', 'ru-RU', 'omazh',  'Omazh — rus, ayol'),
    ('filipp', 'yandex', 'ru-RU', 'filipp', 'Filipp — rus, erkak'),
    ('ermil',  'yandex', 'ru-RU', 'ermil',  'Ermil — rus, erkak'),
    ('zahar',  'yandex', 'ru-RU', 'zahar',  'Zahar — rus, erkak');

-- FK now that the referenced table exists. A campaign whose voice never resolved to a
-- real row would silently fall back to default routing (TtsRouter.resolve) forever —
-- this makes that typo impossible past creation time.
ALTER TABLE campaign
    ADD CONSTRAINT fk_campaign_tts_voice FOREIGN KEY (tts_voice) REFERENCES tts_voice(id);

-- dial_days becomes a real weekday collection (one row per allowed day) instead of a
-- comma-separated VARCHAR — Set<DayOfWeek> on the Java side via @ElementCollection.
CREATE TABLE campaign_dial_day (
    campaign_id BIGINT      NOT NULL REFERENCES campaign(id),
    day         VARCHAR(20) NOT NULL,
    PRIMARY KEY (campaign_id, day)
);

INSERT INTO campaign_dial_day (campaign_id, day)
SELECT c.id, d.day
FROM campaign c, unnest(string_to_array(c.dial_days, ',')) AS d(day)
WHERE c.dial_days IS NOT NULL AND c.dial_days <> '';

ALTER TABLE campaign DROP COLUMN dial_days;
