-- What the model reads about a built-in tool, editable by the platform admin. NULL = the text in
-- code (@Tool); an HTTP tool keeps its model text in definition.description instead.
-- Seeded with today's code text so the admin screen starts from something to edit.
ALTER TABLE integration_tool ADD COLUMN model_description TEXT;

UPDATE integration_tool SET model_description = 'Searches flats ON SALE in the company''s residential complexes. Call it when the client asks what is available, about prices, room counts, floors or a budget. Say ONLY flats and prices returned from here — never invent a flat, price or area.'
 WHERE provider = 'UYSOT' AND tool_key = 'SEARCH_FLATS' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Lists the company''s residential complexes on sale with their address, floors, blocks and planned hand-over date. Call it when the client asks which projects there are, where they are or when they will be finished.'
 WHERE provider = 'UYSOT' AND tool_key = 'LIST_COMPLEXES' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Remembers the flat the client liked or chose, so the next call or chat continues from it. Call it when the client says a specific flat suits them, asks to hold it, or wants to see it.'
 WHERE provider = 'UYSOT' AND tool_key = 'REMEMBER_FLAT_INTEREST' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Reads the client''s contract from the CRM: total, paid, remaining balance, days overdue and the next payment. Call it when the client asks how much is left, how much they owe or when the next payment is. State ONLY the figures returned from here.'
 WHERE provider = 'UYSOT' AND tool_key = 'GET_CONTRACT_BALANCE' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Reads the client''s upcoming and overdue monthly payments from the CRM. Call it when the client asks for their payment schedule or the dates and amounts of coming payments.'
 WHERE provider = 'UYSOT' AND tool_key = 'GET_PAYMENT_SCHEDULE' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Reads ALL of the client''s active contracts and what is owed on each: the remaining balance, how many days it is overdue and which monthly payments are past due. Call it when the client asks how much they owe in total, or when you must know every contract before talking about debt. State ONLY the figures returned from here — never add them up yourself.'
 WHERE provider = 'UYSOT' AND tool_key = 'GET_DEBT_SUMMARY' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Looks the caller up in the CRM by their phone number: their name, their lead and their active contracts. Call it when you need to know who the client is or whether they are already a customer. Say ONLY what is returned from here.'
 WHERE provider = 'UYSOT' AND tool_key = 'GET_CLIENT_PROFILE' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Opens a NEW lead in the CRM for this caller. Call it only when the client is not in the CRM yet (getClientProfile said so) and has shown interest in buying. Never open one for a known client.'
 WHERE provider = 'UYSOT' AND tool_key = 'CREATE_LEAD' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Writes a note on the caller''s lead in the CRM. Call it for something a manager must know that is not captured elsewhere: a preference, a family situation, a promise, an objection.'
 WHERE provider = 'UYSOT' AND tool_key = 'ADD_LEAD_NOTE' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Puts a task for a manager on the caller''s lead in the CRM: a call back or a meeting at an exact time. Call it when the client agreed a concrete day and hour. Do not guess the time.'
 WHERE provider = 'UYSOT' AND tool_key = 'CREATE_FOLLOW_UP_TASK' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Moves the caller''s lead to another stage of the sales funnel. Call it only when the call clearly changed where the client is in the funnel (e.g. they refused, or agreed to a visit). Name the stage exactly as the funnel calls it.'
 WHERE provider = 'UYSOT' AND tool_key = 'MOVE_LEAD_STAGE' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Asks the client''s own sales or contract manager to call them back. Call it when the client wants a person to call, has a question you cannot answer, or wants to discuss terms or a discount.'
 WHERE provider = 'UYSOT' AND tool_key = 'REQUEST_MANAGER_CALLBACK' AND kind = 'BUILT_IN';
UPDATE integration_tool SET model_description = 'Reads the client''s flat bookings from the CRM: the flat, its status and until when it is held. Call it when the client asks about their booking or whether a flat is still held for them. Say ONLY what is returned from here.'
 WHERE provider = 'UYSOT' AND tool_key = 'GET_BOOKING_STATUS' AND kind = 'BUILT_IN';
