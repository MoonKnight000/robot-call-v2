-- The lead write tools stop being code: createLead, addLeadNote, createFollowUpTask and
-- moveLeadStage become HTTP catalog tools under the SAME model names. Nothing Uysot-specific is
-- left in code; what the code looked up itself now comes from the call:
--   * the lead: {{crmLeadId}}, recorded by getClientProfile — called first;
--   * the company's CRM settings: {{crm_new_lead_status_id}}, {{crm_ai_employee_id}},
--     {{crm_task_type_id}} (IntegrationHttpToolFactory);
--   * the task's start: {{now_epoch}}, the request's own time; its deadline: the model's local
--     "yyyy-MM-dd HH:mm", turned into epoch seconds in the agent's timezone ("x-format");
--   * a stage by name: the new listLeadStages tool lists the funnel, moveLeadStage takes the id.
-- A write is not read back to the caller first ("requiresConfirmation": false), as before.

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "createLead",
  "description": "Opens a NEW lead in the CRM for this caller. Call it only when getClientProfile said this number has no lead and the client has shown interest in buying. Never open one for a known client, and never twice on one call. Then tell the client a manager will contact them.",
  "http": {
    "url": "v1/open-api/lead",
    "method": "POST",
    "body": [
      {"name": "__raw__", "type": "string", "valueType": "STATIC_VALUE", "value": "{\"name\": \"{{name}}\", \"pipeStatusId\": {{crm_new_lead_status_id}}, \"responsibleById\": {{crm_ai_employee_id}}, \"contacts\": [{\"name\": \"{{name}}\", \"type\": \"VOICE_AGENT\", \"phones\": [\"{{phone}}\"]}]}"},
      {"name": "name", "type": "string", "valueType": "LLM_PROMPT", "description": "the client's name as they gave it; their phone number if they gave none", "required": true}
    ],
    "responseTimeoutSecs": 8,
    "retryAttempts": 0
  },
  "result": {
    "errorMessage": "THE CRM IS NOT AVAILABLE RIGHT NOW - do not claim that the lead was opened. Tell the client a specialist will take care of it."
  },
  "maxCallsPerCall": 1,
  "effect": "WRITE",
  "requiresConfirmation": false
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'CREATE_LEAD';

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "addLeadNote",
  "description": "Writes a note on the caller's lead in the CRM. Call it for something a manager must know that is not captured elsewhere: a preference, a family situation, a promise, an objection. Needs the lead: call getClientProfile first on this call.",
  "http": {
    "url": "v1/open-api/lead-note/{id}/list",
    "method": "POST",
    "pathParams": [
      {"name": "id", "type": "integer", "valueType": "DYNAMIC_VARIABLE", "value": "crmLeadId"}
    ],
    "body": [
      {"name": "__raw__", "type": "string", "valueType": "STATIC_VALUE", "value": "[{\"text\": \"[AI agent] {{note}}\", \"type\": \"PHONE\", \"isPin\": false}]"},
      {"name": "note", "type": "string", "valueType": "LLM_PROMPT", "description": "the note, one or two sentences, at most 450 characters", "required": true}
    ],
    "responseTimeoutSecs": 8,
    "retryAttempts": 0
  },
  "result": {
    "errorMessage": "THE NOTE WAS NOT SAVED - if getClientProfile was not called on this call, call it first; if it found no lead, this client has none (offer createLead). Otherwise tell the client a specialist will take care of it."
  },
  "maxCallsPerCall": 3,
  "effect": "WRITE",
  "requiresConfirmation": false
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'ADD_LEAD_NOTE';

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "createFollowUpTask",
  "description": "Puts a task for a manager on the caller's lead in the CRM: a call back or a meeting at an exact time. Call it when the client agreed a concrete day and hour; do not guess the time. Needs the lead: call getClientProfile first on this call. Then tell the client a manager will do it at that time.",
  "http": {
    "url": "v1/open-api/lead-task/{id}/save",
    "method": "POST",
    "pathParams": [
      {"name": "id", "type": "integer", "valueType": "DYNAMIC_VARIABLE", "value": "crmLeadId"}
    ],
    "bodySchema": {
      "type": "object",
      "required": ["text", "deadlineFinishTimestamp"],
      "properties": {
        "text": {"type": "string", "description": "what the manager must do, and details, e.g. 'Mijozga qayta qo''ng''iroq qilish: 2 xonali haqida'"},
        "deadlineFinishTimestamp": {"type": "string", "description": "when it is due, local time, format yyyy-MM-dd HH:mm, in the future", "x-format": "epochSeconds"},
        "deadlineStartTimestamp": {"type": "integer", "x-source": "variable", "x-value": "now_epoch"},
        "leadTaskTypeId": {"type": "integer", "x-source": "variable", "x-value": "crm_task_type_id"},
        "assignedById": {"type": "integer", "x-source": "variable", "x-value": "crm_ai_employee_id"}
      }
    },
    "responseTimeoutSecs": 8,
    "retryAttempts": 0
  },
  "result": {
    "errorMessage": "THE TASK WAS NOT SET - if getClientProfile was not called on this call, call it first; if it found no lead, this client has none. Check the time is yyyy-MM-dd HH:mm. Otherwise tell the client a specialist will take care of it.",
    "argumentVariables": {"deadlineFinishTimestamp": "followUpTaskTime"}
  },
  "maxCallsPerCall": 2,
  "effect": "WRITE",
  "requiresConfirmation": false
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'CREATE_FOLLOW_UP_TASK';

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "moveLeadStage",
  "description": "Moves the caller's lead to another stage of the sales funnel. Call it only when the call clearly changed where the client is in the funnel (they refused, or agreed to a visit). Take the stage's id from listLeadStages. Needs the lead: call getClientProfile first on this call.",
  "http": {
    "url": "v1/open-api/lead",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "required": ["pipeStatusId"],
      "properties": {
        "id": {"type": "integer", "x-source": "variable", "x-value": "crmLeadId"},
        "pipeStatusId": {"type": "integer", "description": "the id of the funnel stage, from listLeadStages"}
      }
    },
    "responseTimeoutSecs": 8,
    "retryAttempts": 0
  },
  "result": {
    "errorMessage": "THE LEAD WAS NOT MOVED - if getClientProfile was not called on this call, call it first; if it found no lead, this client has none. Check the stage id comes from listLeadStages."
  },
  "maxCallsPerCall": 2,
  "effect": "WRITE",
  "requiresConfirmation": false
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'MOVE_LEAD_STAGE';

INSERT INTO integration_tool (provider, tool_key, kind, tool_group, label, description, permission, scope,
                              default_enabled, sort_order, definition)
VALUES ('UYSOT', 'LIST_LEAD_STAGES', 'HTTP', 'LEADS', 'Voronka bosqichlari',
        'Voronkalar va ularning bosqichlari (id va nomi) — moveLeadStage uchun.', 'LEAD', 'READ', TRUE, 105,
        $json$
{
  "name": "listLeadStages",
  "description": "Lists the company's sales funnels and their stages, each with its id and name. Call it before moveLeadStage to find the id of the stage the lead should go to. Say ONLY stage names returned from here.",
  "http": {
    "url": "v1/open-api/pipe/all",
    "method": "GET",
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "funnels": "$.data[*]{id,name,statuses,pipeStatuses,pipeStatusList} || $.data.data[*]{id,name,statuses,pipeStatuses,pipeStatusList}"
  },
  "result": {
    "errorMessage": "THE FUNNEL IS NOT AVAILABLE RIGHT NOW - do not move the lead. Carry on with the conversation."
  },
  "maxCallsPerCall": 2,
  "effect": "READ"
}
$json$::jsonb)
ON CONFLICT (provider, tool_key) DO NOTHING;

-- An agent that moves leads gets the stage list beside it.
UPDATE ai_agent a
SET functions = a.functions || $json$
[{"id": "fn_int_listLeadStages", "type": "INTEGRATION", "name": "listLeadStages", "speakDuringExecution": false, "integrationTool": "listLeadStages"}]
$json$::jsonb
WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(a.functions) f
              WHERE f->>'type' = 'INTEGRATION' AND f->>'integrationTool' = 'moveLeadStage')
  AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(a.functions) f
                  WHERE f->>'integrationTool' = 'listLeadStages');
