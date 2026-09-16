-- UNIVERSAL-OPERATOR-PLAN A.1: two scenario-neutral outcomes beside the debt-specific ones.
-- A scenario that marks one outcome field as its goal (OutcomeField.goal) ends GOAL_REACHED
-- when the call recorded it and GOAL_NOT_REACHED when it did not. PROMISE_TO_PAY and
-- REFUSED stay as they are for the debt scenario — no existing row changes.
ALTER TABLE call_attempt DROP CONSTRAINT ck_call_attempt_disposition;
ALTER TABLE call_attempt ADD CONSTRAINT ck_call_attempt_disposition
    CHECK (disposition IS NULL OR disposition IN (
        'PROMISE_TO_PAY', 'REFUSED', 'NO_ANSWER', 'CARRIER_REJECTED', 'WRONG_NUMBER', 'DO_NOT_CALL',
        'HUNG_UP', 'TRANSFERRED', 'VOICEMAIL', 'CALLBACK_REQUESTED', 'FAILED', 'COMPLETED',
        'GOAL_REACHED', 'GOAL_NOT_REACHED'));
