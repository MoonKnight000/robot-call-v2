-- rememberFlatInterest and requestManagerCallback stop being code: each becomes the agent's own
-- EXTRACT_VARIABLE function under the same name (Retell's "Extract Dynamic Variable"), editable
-- in the studio. What the code wrote is what the function records:
--   * rememberFlatInterest -> interestedFlat, kept in the client's memory too ("remember": true);
--   * requestManagerCallback -> managerCallbackRequested / managerCallbackReason / managerCallbackTime,
--     the call outcome the CRM task after the call is made from.
-- The catalog rows behind them go: there is no code left to run them.

UPDATE ai_agent a
SET functions = (
    SELECT jsonb_agg(
               CASE
                   WHEN f->>'type' = 'INTEGRATION' AND f->>'integrationTool' = 'rememberFlatInterest' THEN $json$
{"id": "fn_extract_rememberFlatInterest", "type": "EXTRACT_VARIABLE", "name": "rememberFlatInterest",
 "description": "Remembers the flat the client liked or chose, so the next call or chat continues from it. Call it when the client says a specific flat suits them, asks to hold it, or wants to see it.",
 "speakDuringExecution": false,
 "extract": {"variables": [
   {"name": "interestedFlat", "type": "STRING", "description": "the flat as it was offered (complex, block, number, rooms, area, price) and what the client said about it", "choices": [], "remember": true}
 ]}}
$json$::jsonb
                   WHEN f->>'type' = 'INTEGRATION' AND f->>'integrationTool' = 'requestManagerCallback' THEN $json$
{"id": "fn_extract_requestManagerCallback", "type": "EXTRACT_VARIABLE", "name": "requestManagerCallback",
 "description": "Asks the client's own sales or contract manager to call them back. Call it when the client wants a person to call, has a question you cannot answer, or wants to discuss terms or a discount. Then tell the client their manager will call them back.",
 "speakDuringExecution": false,
 "extract": {"variables": [
   {"name": "managerCallbackRequested", "type": "BOOLEAN", "description": "always true: the client asked for a manager to call back", "choices": [], "remember": false},
   {"name": "managerCallbackReason", "type": "STRING", "description": "what the client wants to discuss", "choices": [], "remember": false},
   {"name": "managerCallbackTime", "type": "STRING", "description": "when the client prefers to be called, if they said", "choices": [], "remember": false}
 ]}}
$json$::jsonb
                   ELSE f
               END ORDER BY ord)
    FROM jsonb_array_elements(a.functions) WITH ORDINALITY AS t(f, ord))
WHERE EXISTS (SELECT 1
              FROM jsonb_array_elements(a.functions) f
              WHERE f->>'type' = 'INTEGRATION'
                AND f->>'integrationTool' IN ('rememberFlatInterest', 'requestManagerCallback'));

DELETE FROM integration_tool
WHERE provider = 'UYSOT' AND tool_key IN ('REMEMBER_FLAT_INTEREST', 'REQUEST_MANAGER_CALLBACK');
