-- Where each remembered fact came from (the call, chat, tool or operator that wrote it, and when):
-- fact name -> {kind, callAttemptId, chatSessionId, tool, at}. Facts written before this have no entry.
ALTER TABLE client_memory ADD COLUMN fact_sources JSONB NOT NULL DEFAULT '{}';
