-- An HTTP tool's definition: a tool.domain Tool as JSON whose http.url is a path relative to
-- the integration's host. NULL for BUILT_IN tools, whose handler is code.
ALTER TABLE integration_tool ADD COLUMN definition JSONB;
