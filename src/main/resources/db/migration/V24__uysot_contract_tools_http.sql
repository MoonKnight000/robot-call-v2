-- Uysot's contract balance and payment schedule stop being code: they become HTTP catalog tools
-- under the SAME model names (getContractBalance, getPaymentSchedule), so the agents' functions
-- keep working. What the code did beside the request is now the tool's own settings:
--   * a contract number the client did not say comes from the call (contractNumber), else the
--     client's phone (phone_last9) — bodySchema "x-fallback";
--   * the contract's number and its responsible manager become call outcomes (contractNumber,
--     responsibleEmployeeId / responsibleEmployeeName) through responseMapping;
--   * only the unpaid payments are shown — a "[?…]" filter in responseMapping.
-- The schedule now takes the contract's id from getContractBalance's answer, and the balance no
-- longer states the next payment: the model asks getPaymentSchedule for it.
--
-- Their HTTP twins (*_LIVE, V1) are deleted; a company's choice of a twin goes with it.

UPDATE integration_tool
SET kind              = 'HTTP',
    description       = 'Shartnoma summasi, to''langani, qolgan qarz va kechikish kunlari.',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "getContractBalance",
  "description": "Reads the client's contract from the CRM: total, paid, remaining balance (residue), days overdue (delay) and status. Call it when the client asks how much is left or how much they owe. Pass the contract number only if the client named one; otherwise leave it empty and the client's own contract is found. If several contracts come back, talk about the one with the largest delay. For the next payment call getPaymentSchedule with the contract's id. State ONLY the figures returned from here.",
  "http": {
    "url": "v1/open-api/contract/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 5},
        "statuses": {"type": "array", "x-source": "static", "x-value": ["ACTIVE", "STARTED"]},
        "search": {"type": "string", "description": "the contract number the client named, exactly as they said it; empty when they named none", "x-fallback": ["contractNumber", "phone_last9"]}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "contracts": "$.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.content[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status}",
    "contractNumber": "$.data[0].number || $.data.data[0].number || $.data.content[0].number",
    "responsibleEmployeeId": "$.data[0].responsibleBy.id || $.data.data[0].responsibleBy.id || $.data.content[0].responsibleBy.id || $.data[0].responsibleById || $.data.data[0].responsibleById || $.data.content[0].responsibleById",
    "responsibleEmployeeName": "$.data[0].responsibleBy.fullName || $.data.data[0].responsibleBy.fullName || $.data.content[0].responsibleBy.fullName || $.data[0].responsibleBy.name || $.data.data[0].responsibleBy.name || $.data.content[0].responsibleBy.name"
  },
  "result": {
    "emptyMessage": "NO CONTRACT FOUND for this client - do not invent an amount. Ask for the contract number, or offer a callback from their manager.",
    "errorMessage": "THE DATA IS NOT AVAILABLE RIGHT NOW - do not invent an amount. This is not a question from the client: do not say you have no information, do not say you are an AI and do not transfer the call. Carry on with the conversation and offer a callback from their manager."
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'GET_CONTRACT_BALANCE';

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "getPaymentSchedule",
  "description": "Reads one contract's UNPAID monthly payments from the CRM, earliest first: date, amount and what is still unpaid. Needs the contract's id: take it from the id of the contract returned by getContractBalance (call that first). Call it when the client asks when the next payment is, for their payment schedule or which payments are overdue; a payment dated before today is overdue. State ONLY the dates and amounts returned from here.",
  "http": {
    "url": "v1/open-api/contract/{contractId}/monthly-payment",
    "method": "GET",
    "pathParams": [
      {"name": "contractId", "type": "integer", "valueType": "LLM_PROMPT", "description": "the contract's id, from getContractBalance", "required": true}
    ],
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "payments": "$.data[?paid!=true&paidStatus!=PAID&status!=PAID]{date,paymentDate,dueDate,amount,unpaidAmount,residue,remaining,payment,paidAmount,payedAmount} || $.data.data[?paid!=true&paidStatus!=PAID&status!=PAID]{date,paymentDate,dueDate,amount,unpaidAmount,residue,remaining,payment,paidAmount,payedAmount} || $.data.content[?paid!=true&paidStatus!=PAID&status!=PAID]{date,paymentDate,dueDate,amount,unpaidAmount,residue,remaining,payment,paidAmount,payedAmount}"
  },
  "result": {
    "maxItems": 3,
    "emptyMessage": "This contract has no unpaid scheduled payments - say so; do not invent a date or an amount.",
    "errorMessage": "THE DATA IS NOT AVAILABLE RIGHT NOW - do not invent a date or an amount. This is not a question from the client: do not say you have no information, do not say you are an AI and do not transfer the call. Carry on with the conversation and offer a callback from their manager."
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'GET_PAYMENT_SCHEDULE';

DELETE FROM integration_tool
WHERE provider = 'UYSOT' AND tool_key IN ('GET_CONTRACT_BALANCE_LIVE', 'GET_PAYMENT_SCHEDULE_LIVE');
