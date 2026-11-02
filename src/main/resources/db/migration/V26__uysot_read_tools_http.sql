-- The last read-only Uysot tools stop being code: getClientProfile, getDebtSummary and
-- getBookingStatus become HTTP catalog tools under the SAME model names, so the agents'
-- functions and the scenarios that name them (GET_PROFILE) keep working.
--   * getClientProfile reads the lead only (POST lead/filter by the phone); the client's contracts
--     are getDebtSummary's. The lead's id becomes the call's crmLeadId, for the lead tools after it.
--   * getDebtSummary reads every active contract by the phone; the past-due payments of one
--     come from getPaymentSchedule with its id. The first contract's manager becomes the call's
--     responsibleEmployeeId / responsibleEmployeeName, as getContractBalance does.
--   * getBookingStatus reads the client's bookings by the phone.
--
-- The HTTP twins of the last two (*_LIVE, V1) are deleted; a company's choice of a twin goes with it.

UPDATE integration_tool
SET kind              = 'HTTP',
    description       = 'Raqam bo''yicha mijozni topish: ismi va lidi.',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "getClientProfile",
  "description": "Looks the caller up in the CRM by their phone number: their name and their lead. Call it when you need to know who the client is or whether they are already in the CRM. For their contracts and debts call getDebtSummary. Say ONLY what is returned from here.",
  "http": {
    "url": "v1/open-api/lead/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 1},
        "phone": {"type": "string", "x-source": "variable", "x-value": "phone_last9"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "lead": "$.data.data[0]{id,name,clientName,fullName,status,pipeStatus.name} || $.data[0]{id,name,clientName,fullName,status,pipeStatus.name}",
    "crmLeadId": "$.data.data[0].id || $.data[0].id"
  },
  "result": {
    "emptyMessage": "NOT A KNOWN CLIENT: this number has no lead in the CRM. Do not invent a name.",
    "errorMessage": "THE CRM IS NOT AVAILABLE RIGHT NOW - do not claim that the client was found. Carry on with the conversation."
  },
  "maxCallsPerCall": 2,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'GET_CLIENT_PROFILE';

UPDATE integration_tool
SET kind              = 'HTTP',
    description       = 'Mijozning barcha faol shartnomalari bo''yicha qarz va kechikish.',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "getDebtSummary",
  "description": "Reads ALL of the client's active contracts and what is owed on each: the remaining balance (residue), days overdue (delay) and status. Call it when the client asks how much they owe in total, or when you must know every contract before talking about debt. Talk about the contract with the largest delay first. For the past-due payments of a contract call getPaymentSchedule with its id. State ONLY the figures returned from here - never add them up yourself.",
  "http": {
    "url": "v1/open-api/contract/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 20},
        "statuses": {"type": "array", "x-source": "static", "x-value": ["ACTIVE", "STARTED"]},
        "search": {"type": "string", "x-source": "variable", "x-value": "phone_last9"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "contracts": "$.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.content[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status}",
    "responsibleEmployeeId": "$.data[0].responsibleBy.id || $.data.data[0].responsibleBy.id || $.data.content[0].responsibleBy.id || $.data[0].responsibleById || $.data.data[0].responsibleById || $.data.content[0].responsibleById",
    "responsibleEmployeeName": "$.data[0].responsibleBy.fullName || $.data.data[0].responsibleBy.fullName || $.data.content[0].responsibleBy.fullName || $.data[0].responsibleBy.name || $.data.data[0].responsibleBy.name || $.data.content[0].responsibleBy.name"
  },
  "result": {
    "maxItems": 3,
    "emptyMessage": "NO CONTRACT FOUND for this client - do not invent an amount. Ask for the contract number, or offer a callback from their manager.",
    "errorMessage": "THE DATA IS NOT AVAILABLE RIGHT NOW - do not invent an amount. This is not a question from the client: do not say you have no information, do not say you are an AI and do not transfer the call. Carry on with the conversation and offer a callback from their manager."
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'GET_DEBT_SUMMARY';

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "getBookingStatus",
  "description": "Reads the client's flat bookings from the CRM: the flat, its status, until when it is held and the booking fee. Call it when the client asks about their booking or whether a flat is still held for them. Say ONLY what is returned from here.",
  "http": {
    "url": "v1/open-api/booking/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 5},
        "search": {"type": "string", "x-source": "variable", "x-value": "phone_last9"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "bookings": "$.data[*]{id,house.name,houseName,flat.number,flatNumber,number,status,bookingStatus,expireDate,expiresAt,endDate,deadline,amount,bookingAmount,currency.ccy,currencyCcy} || $.data.data[*]{id,house.name,houseName,flat.number,flatNumber,number,status,bookingStatus,expireDate,expiresAt,endDate,deadline,amount,bookingAmount,currency.ccy,currencyCcy} || $.data.content[*]{id,house.name,houseName,flat.number,flatNumber,number,status,bookingStatus,expireDate,expiresAt,endDate,deadline,amount,bookingAmount,currency.ccy,currencyCcy}"
  },
  "result": {
    "emptyMessage": "NO BOOKINGS found for this client. Do not invent one.",
    "errorMessage": "THE CRM IS NOT AVAILABLE RIGHT NOW - do not claim a booking was found. Tell the client a specialist will check it."
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'GET_BOOKING_STATUS';

DELETE FROM integration_tool
WHERE provider = 'UYSOT' AND tool_key IN ('GET_BOOKING_STATUS_LIVE', 'GET_DEBT_SUMMARY_LIVE');
