-- The outcome tools that used to be code (DialogTools: recordPaymentPromise, recordRefusalReason,
-- scheduleCallback, recordPaymentClaim, requestPaymentExtension, bookShowroomVisit, bookAppointment,
-- cancelAppointment) are now built from the scenario's own ToolDef — its params are the outcome keys it
-- records, and ToolDef.disposition is what a call of it makes of the conversation.
--
-- A scenario saved before this ignored its params for these names (the code method's own were used),
-- so its declaration is replaced by the canonical one below, not patched. A tool the code sent an SMS
-- or invented slots for (sendSmsPaymentLink, sendLocationSms, sendCatalogSms, checkCalendarAvailability)
-- has no scenario form — SMS goes through the agent's midCallSmsTemplate (sendMidCallSms) — and is dropped.
-- A stage's allowedTools may still name a dropped tool; that is harmless at runtime and is reported by the
-- scenario validator the next time the scenario is saved.

CREATE TEMP TABLE static_tool_canonical (name TEXT PRIMARY KEY, definition JSONB) ON COMMIT DROP;

INSERT INTO static_tool_canonical (name, definition) VALUES
('recordPaymentPromise', $j$
{"name": "recordPaymentPromise", "disposition": "PROMISE_TO_PAY",
 "description": "Call when the client names an EXACT DAY themselves (e.g. \"10-oktabr\", \"ertaga\"). Do NOT call it on a vague answer like \"keyingi oy\", \"oy oxirida\", \"pul bo'lganda\" — first ask which day. Guessing the date yourself is FORBIDDEN",
 "params": [
   {"name": "promisedDate", "type": "futureDate", "required": true, "constraint": "promised date, today or later"},
   {"name": "promisedAmount", "type": "number", "required": false, "constraint": "promised amount"},
   {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER. Leave empty if the client did not say"},
   {"name": "note", "type": "string", "required": false, "constraint": "additional note"}]}
$j$::jsonb),
('recordRefusalReason', $j$
{"name": "recordRefusalReason", "disposition": "REFUSED",
 "description": "Records the reason when the client says they cannot pay",
 "params": [
   {"name": "reasonCode", "type": "string", "required": true, "constraint": "NO_MONEY, JOB_LOSS, ILLNESS, ALREADY_PAID, DISPUTES_DEBT, FORGOT, TECHNICAL_ISSUE or OTHER"},
   {"name": "reasonDetail", "type": "string", "required": false, "constraint": "details of the reason"}]}
$j$::jsonb),
('scheduleCallback', $j$
{"name": "scheduleCallback", "disposition": "CALLBACK_REQUESTED",
 "description": "Call when the client asks to be called back later (e.g. tomorrow or at 16:00)",
 "params": [
   {"name": "callbackTime", "type": "string", "required": true, "constraint": "callback date and time (e.g. 2026-09-02T16:00:00 or 'ertaga soat 16:00')"},
   {"name": "callbackReason", "type": "string", "required": false, "constraint": "reason or note"}]}
$j$::jsonb),
('recordPaymentClaim', $j$
{"name": "recordPaymentClaim", "disposition": "PAYMENT_CLAIMED",
 "description": "Call when the client says they have ALREADY paid the debt. First ask when and through which payment system they paid. Do not argue with the client: tell them they will not be called until the payment has been checked",
 "params": [
   {"name": "paidDate", "type": "pastDate", "required": false, "constraint": "payment date the client named, today or earlier"},
   {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER"}]}
$j$::jsonb),
('requestPaymentExtension', $j$
{"name": "requestPaymentExtension",
 "description": "Call when the client asks to extend (postpone) the payment deadline by a few days",
 "params": [
   {"name": "extensionRequestedDays", "type": "number", "required": true, "constraint": "how many days the extension is for"},
   {"name": "extensionReason", "type": "string", "required": true, "constraint": "reason for the delay"}]}
$j$::jsonb),
('bookShowroomVisit', $j$
{"name": "bookShowroomVisit", "disposition": "GOAL_REACHED",
 "description": "Books a meeting when the client agrees to come to the sales office (showroom). Call only when the client clearly agrees to visit.",
 "params": [
   {"name": "meetingTime", "type": "string", "required": true, "constraint": "meeting date and time (e.g. '2026-09-20 15:00' or 'ertaga soat 15:00')"},
   {"name": "roomsNeeded", "type": "string", "required": false, "constraint": "number of rooms needed (e.g. '2 xonali')"},
   {"name": "budgetRange", "type": "string", "required": false, "constraint": "approximate budget"},
   {"name": "paymentMethod", "type": "string", "required": false, "constraint": "payment method (100% payment, instalments, mortgage)"},
   {"name": "meetingNote", "type": "string", "required": false, "constraint": "additional note"}]}
$j$::jsonb),
('bookAppointment', $j$
{"name": "bookAppointment", "disposition": "GOAL_REACHED",
 "description": "Call when the client agrees to book a service, consultation or appointment. Records the appointment date, time, the client's name and the service type.",
 "params": [
   {"name": "appointmentDate", "type": "string", "required": true, "constraint": "appointment date (e.g. '2026-10-15' or 'ertaga')"},
   {"name": "appointmentTime", "type": "string", "required": true, "constraint": "appointment time (e.g. '14:00' or '15:30')"},
   {"name": "appointmentAttendee", "type": "string", "required": false, "constraint": "client's name"},
   {"name": "appointmentService", "type": "string", "required": false, "constraint": "service or consultation type"},
   {"name": "appointmentNote", "type": "string", "required": false, "constraint": "additional note or requirements"}]}
$j$::jsonb),
('cancelAppointment', $j$
{"name": "cancelAppointment",
 "description": "Call when the client asks to cancel an appointment booked earlier.",
 "params": [
   {"name": "cancelledAppointmentDate", "type": "string", "required": false, "constraint": "date of the appointment being cancelled"},
   {"name": "cancelledAppointmentTime", "type": "string", "required": false, "constraint": "time of the appointment being cancelled"},
   {"name": "cancellationReason", "type": "string", "required": false, "constraint": "reason for cancelling"}]}
$j$::jsonb);

-- Tools with no scenario form: dropped from every definition that declares them.
CREATE TEMP TABLE static_tool_dropped (name TEXT PRIMARY KEY) ON COMMIT DROP;
INSERT INTO static_tool_dropped (name) VALUES
    ('sendSmsPaymentLink'), ('sendLocationSms'), ('sendCatalogSms'), ('checkCalendarAvailability');

UPDATE scenario s
SET definition = jsonb_set(s.definition, '{tools}', COALESCE((
    SELECT jsonb_agg(COALESCE(c.definition, t.tool) ORDER BY t.position)
    FROM jsonb_array_elements(s.definition -> 'tools') WITH ORDINALITY AS t(tool, position)
    LEFT JOIN static_tool_canonical c ON c.name = t.tool ->> 'name'
    WHERE t.tool ->> 'name' NOT IN (SELECT name FROM static_tool_dropped)
), '[]'::jsonb))
WHERE jsonb_typeof(s.definition -> 'tools') = 'array'
  AND EXISTS (
    SELECT 1 FROM jsonb_array_elements(s.definition -> 'tools') e
    WHERE e ->> 'name' IN (SELECT name FROM static_tool_canonical UNION SELECT name FROM static_tool_dropped));

UPDATE ai_agent a
SET scenario_definition = jsonb_set(a.scenario_definition, '{tools}', COALESCE((
    SELECT jsonb_agg(COALESCE(c.definition, t.tool) ORDER BY t.position)
    FROM jsonb_array_elements(a.scenario_definition -> 'tools') WITH ORDINALITY AS t(tool, position)
    LEFT JOIN static_tool_canonical c ON c.name = t.tool ->> 'name'
    WHERE t.tool ->> 'name' NOT IN (SELECT name FROM static_tool_dropped)
), '[]'::jsonb))
WHERE jsonb_typeof(a.scenario_definition -> 'tools') = 'array'
  AND EXISTS (
    SELECT 1 FROM jsonb_array_elements(a.scenario_definition -> 'tools') e
    WHERE e ->> 'name' IN (SELECT name FROM static_tool_canonical UNION SELECT name FROM static_tool_dropped));
