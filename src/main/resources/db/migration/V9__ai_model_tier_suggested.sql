-- How the model picker groups and flags the catalog. tier ranks a chat model by what it is for
-- (FAST: short, cheap answers; BALANCED: the everyday choice; SMART: hard conversations); NULL for
-- rows that are not tiered (speech models, realtime engines). suggested marks the models the
-- picker puts forward first. Both are seeded in R__seed_data.sql.
ALTER TABLE ai_model ADD COLUMN tier VARCHAR(20);
ALTER TABLE ai_model ADD COLUMN suggested BOOLEAN NOT NULL DEFAULT false;
