-- Owner decision (2026-09-14, docs/AUDIT-2026-09-13.md §2 M-12): ai_agent.conversation_retention_days
-- used to be stored but never read, and every call's transcript/recording was purged by the one
-- global period (voice-agent.retention, default 90 days). RetentionService now honours the
-- per-agent value, whose column default is 30 — so the first nightly run would have deleted
-- 30–90 day old conversations nobody asked to delete. Existing agents are lifted to the old
-- global period; 0 (keep forever) and values above 90 stay as they are, and new agents still
-- start at 30. If STORAGE_RETENTION_DAYS was set to something other than 90, adjust this number.
UPDATE ai_agent
SET conversation_retention_days = 90
WHERE conversation_retention_days > 0
  AND conversation_retention_days < 90;
