-- Campaign type is no longer required or enforced; the purpose belongs to AiAgent and Scenario.
ALTER TABLE campaign ALTER COLUMN type DROP NOT NULL;
ALTER TABLE campaign ALTER COLUMN type SET DEFAULT 'OUTBOUND';
