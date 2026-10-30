-- CRM tools the company switched off for its voice agent (CrmTool names, comma-separated).
-- Superseded by V13 (integration_tool catalog + company_integration_tool), which moves these
-- values into rows and drops the column.
ALTER TABLE crm_integration ADD COLUMN disabled_tools TEXT;
