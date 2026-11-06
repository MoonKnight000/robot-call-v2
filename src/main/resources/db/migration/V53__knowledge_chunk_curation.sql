-- Curating what an agent answers from (KNOWLEDGE-MEMORY-PLAN phase 2).
--
-- disabled:      the passage stays stored but is never searched — a wrong or outdated paragraph switched off
--                without editing the document.
-- original_hash: set when a person edited the passage; the hash of the text the indexer produced. A refresh
--                that reads the same original text again keeps the person's edit instead of overwriting it.
ALTER TABLE knowledge_chunk
    ADD COLUMN disabled      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN original_hash VARCHAR(64);

-- A source switched off keeps its passages and stops answering, without being deleted and indexed again.
ALTER TABLE knowledge_source
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;
