-- The payment-promise feature (a table of promises, their reminders and kept/broken follow-up) is gone:
-- a promise to pay is now only what the call recorded — Disposition.PROMISE_TO_PAY and the
-- promised_date / promised_amount of call_result, written by the scenario's own outcome tool.
-- The history in these two tables is not kept.
DROP TABLE IF EXISTS payment_promise;
DROP TABLE IF EXISTS payment_promise_settings;
