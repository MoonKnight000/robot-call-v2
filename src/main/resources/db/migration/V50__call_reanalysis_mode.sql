-- Contact backfill rides on the reanalysis job: ANALYSIS (as before), CONTACTS_STORED, CONTACTS_REEXTRACT.
ALTER TABLE call_reanalysis_job ADD COLUMN mode VARCHAR(32) NOT NULL DEFAULT 'ANALYSIS';
