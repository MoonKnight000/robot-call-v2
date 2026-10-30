-- How a tool's request is run (Retell-style execution mode):
--   wait_for_result  TRUE (default): the model waits for the answer; FALSE: the request runs in the
--                    background and the model carries on.
--   status_variable  call outcome field set to 'success' / 'failure' once the request has finished.
ALTER TABLE tool
    ADD COLUMN wait_for_result BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN status_variable VARCHAR(64);
