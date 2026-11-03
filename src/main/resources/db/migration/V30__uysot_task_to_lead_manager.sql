-- A follow-up task goes to the lead's own manager, as the coded CRM task always did
-- (UysotConnectorAdapter#createTask); the AI employee only when the lead names none.
--   * getClientProfile also records the lead's manager as {{crmResponsibleId}};
--   * createFollowUpTask's assignee is that, else the LEAD_CREATED webhook's {{responsibleId}},
--     else {{crm_ai_employee_id}} — an x-source variable field with x-fallback.
-- An agent can still pin its own assignee on the function form (ToolOverrides), as on Retell.

UPDATE integration_tool
SET definition = jsonb_set(definition, '{responseMapping,crmResponsibleId}',
        '"$.data.data[0].responsibleById || $.data.data[0].responsibleBy.id || $.data[0].responsibleById || $.data[0].responsibleBy.id"'),
    updated_at = now()
WHERE provider = 'UYSOT' AND tool_key = 'GET_CLIENT_PROFILE';

UPDATE integration_tool
SET definition = jsonb_set(definition, '{http,bodySchema,properties,assignedById}',
        '{"type": "integer", "x-source": "variable", "x-value": "crmResponsibleId", "x-fallback": ["responsibleId", "crm_ai_employee_id"]}'),
    updated_at = now()
WHERE provider = 'UYSOT' AND tool_key = 'CREATE_FOLLOW_UP_TASK';
