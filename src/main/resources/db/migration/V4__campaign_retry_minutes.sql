-- Campaign retry interval in minutes, and it now drives every disposition.
--
-- Hours were too coarse for the thing the field actually describes: how long before this
-- client is dialled again. A campaign that wants a second attempt ten minutes later could
-- not say so, and the per-disposition defaults (voice-agent.dialer.retry.*) were the only
-- way to change a NO_ANSWER retry at all — process-wide, for every campaign at once.
--
-- 0 means "no campaign preference": fall back to those per-disposition defaults. Existing
-- rows still on the old 24-hour default are moved to 0, because that is exactly what they
-- behaved like — the 24 hours only ever applied to dispositions without a default of their
-- own. A row someone deliberately changed keeps its value, converted to minutes.
ALTER TABLE campaign RENAME COLUMN retry_interval_hours TO retry_interval_minutes;

UPDATE campaign
SET retry_interval_minutes = CASE WHEN retry_interval_minutes = 24 THEN 0
                                  ELSE retry_interval_minutes * 60 END;

ALTER TABLE campaign ALTER COLUMN retry_interval_minutes SET DEFAULT 0;
