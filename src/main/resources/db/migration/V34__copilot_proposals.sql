-- Copilot proposals (B6): a write the copilot wanted to make is kept as a TOOL row with
-- proposal_status PENDING until the user applies or rejects it in the panel; NOTE rows tell the
-- model, in later questions, what the user decided.
ALTER TABLE copilot_message ADD COLUMN proposal_status VARCHAR(16);
ALTER TABLE copilot_message ADD CONSTRAINT ck_copilot_message_proposal_status
    CHECK (proposal_status IS NULL OR proposal_status IN ('PENDING', 'APPLIED', 'REJECTED', 'FAILED'));
ALTER TABLE copilot_message DROP CONSTRAINT ck_copilot_message_role;
ALTER TABLE copilot_message ADD CONSTRAINT ck_copilot_message_role
    CHECK (role IN ('USER', 'ASSISTANT', 'TOOL', 'NOTE'));
