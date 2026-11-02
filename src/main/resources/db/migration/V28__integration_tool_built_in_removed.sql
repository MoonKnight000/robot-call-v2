-- No integration tool is code any more (V23–V27 turned the last ones into HTTP tools), so the
-- catalog loses what only a built-in tool had: its kind and the model text kept beside the code.
-- Every row is an HTTP tool and has a definition.
--
-- An agent could name a built-in tool by its catalog key (CREATE_LEAD) in its function list or
-- in disabledIntegrationTools; the code that matched a key to the model's name is gone, so those
-- entries are rewritten to the name — on the draft and on every saved version.

ALTER TABLE integration_tool DROP COLUMN kind;
ALTER TABLE integration_tool DROP COLUMN model_description;
ALTER TABLE integration_tool ALTER COLUMN definition SET NOT NULL;

CREATE TEMPORARY TABLE built_in_tool_name (tool_key TEXT PRIMARY KEY, tool_name TEXT NOT NULL) ON COMMIT DROP;
INSERT INTO built_in_tool_name VALUES
    ('CREATE_LEAD', 'createLead'),
    ('ADD_LEAD_NOTE', 'addLeadNote'),
    ('CREATE_FOLLOW_UP_TASK', 'createFollowUpTask'),
    ('MOVE_LEAD_STAGE', 'moveLeadStage');

-- disabledIntegrationTools: a list of strings.
UPDATE ai_agent a
SET disabled_integration_tools = (
    SELECT COALESCE(jsonb_agg(to_jsonb(COALESCE(n.tool_name, e.entry)) ORDER BY e.ord), '[]'::jsonb)
    FROM jsonb_array_elements_text(a.disabled_integration_tools) WITH ORDINALITY AS e(entry, ord)
    LEFT JOIN built_in_tool_name n ON n.tool_key = btrim(e.entry))
WHERE EXISTS (SELECT 1 FROM jsonb_array_elements_text(a.disabled_integration_tools) e
              JOIN built_in_tool_name n ON n.tool_key = btrim(e));

-- functions: an INTEGRATION function's integrationTool.
UPDATE ai_agent a
SET functions = (
    SELECT jsonb_agg(CASE WHEN n.tool_name IS NULL THEN f.fn
                          ELSE jsonb_set(f.fn, '{integrationTool}', to_jsonb(n.tool_name)) END ORDER BY f.ord)
    FROM jsonb_array_elements(a.functions) WITH ORDINALITY AS f(fn, ord)
    LEFT JOIN built_in_tool_name n ON n.tool_key = btrim(f.fn->>'integrationTool'))
WHERE jsonb_typeof(a.functions) = 'array'
  AND EXISTS (SELECT 1 FROM jsonb_array_elements(a.functions) f
              JOIN built_in_tool_name n ON n.tool_key = btrim(f->>'integrationTool'));

-- The same two lists inside each saved version's snapshot.
UPDATE ai_agent_version v
SET snapshot = jsonb_set(v.snapshot, '{disabledIntegrationTools}', (
    SELECT COALESCE(jsonb_agg(to_jsonb(COALESCE(n.tool_name, e.entry)) ORDER BY e.ord), '[]'::jsonb)
    FROM jsonb_array_elements_text(v.snapshot->'disabledIntegrationTools') WITH ORDINALITY AS e(entry, ord)
    LEFT JOIN built_in_tool_name n ON n.tool_key = btrim(e.entry)))
WHERE jsonb_typeof(v.snapshot->'disabledIntegrationTools') = 'array'
  AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(v.snapshot->'disabledIntegrationTools') e
              JOIN built_in_tool_name n ON n.tool_key = btrim(e));

UPDATE ai_agent_version v
SET snapshot = jsonb_set(v.snapshot, '{functions}', (
    SELECT jsonb_agg(CASE WHEN n.tool_name IS NULL THEN f.fn
                          ELSE jsonb_set(f.fn, '{integrationTool}', to_jsonb(n.tool_name)) END ORDER BY f.ord)
    FROM jsonb_array_elements(v.snapshot->'functions') WITH ORDINALITY AS f(fn, ord)
    LEFT JOIN built_in_tool_name n ON n.tool_key = btrim(f.fn->>'integrationTool')))
WHERE jsonb_typeof(v.snapshot->'functions') = 'array'
  AND EXISTS (SELECT 1 FROM jsonb_array_elements(v.snapshot->'functions') f
              JOIN built_in_tool_name n ON n.tool_key = btrim(f->>'integrationTool'));
